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

@Service
public class LlmClientService {

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public LlmClientService(LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    public String chat(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(llmProperties.getBaseUrl()) || !StringUtils.hasText(llmProperties.getApiToken())) {
            return "知识库已命中相关政策片段，但当前未配置大模型服务，请先在环境变量中设置 LLM_BASE_URL 和 LLM_API_TOKEN。";
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiToken());

        Map<String, Object> body = Map.of(
                "model", llmProperties.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    llmProperties.getBaseUrl() + "/chat/completions",
                    request,
                    String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new RuntimeException("模型返回为空");
            }
            return choices.get(0).path("message").path("content").asText("");
        } catch (RestClientException e) {
            throw new RuntimeException("调用大模型失败");
        } catch (Exception e) {
            throw new RuntimeException("解析大模型返回失败");
        }
    }
}
