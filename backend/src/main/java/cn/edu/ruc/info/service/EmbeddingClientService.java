package cn.edu.ruc.info.service;

import cn.edu.ruc.info.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EmbeddingClientService {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicBoolean disabled = new AtomicBoolean(false);

    public EmbeddingClientService(LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    public String getEmbeddingModel() {
        return llmProperties.getEmbeddingModel();
    }

    public float[] embed(String input) {
        if (disabled.get()) {
            return null;
        }
        if (!StringUtils.hasText(llmProperties.getBaseUrl())
                || !StringUtils.hasText(llmProperties.getApiToken())
                || !StringUtils.hasText(llmProperties.getEmbeddingModel())) {
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiToken());

        Map<String, Object> body = Map.of(
                "model", llmProperties.getEmbeddingModel(),
                "input", input);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    llmProperties.getBaseUrl() + "/embeddings",
                    request,
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                disabled.set(true);
                return null;
            }
            JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()) {
                disabled.set(true);
                return null;
            }
            float[] vector = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                vector[i] = (float) embedding.get(i).asDouble();
            }
            return vector;
        } catch (RestClientException e) {
            disabled.set(true);
            return null;
        } catch (Exception e) {
            disabled.set(true);
            return null;
        }
    }
}
