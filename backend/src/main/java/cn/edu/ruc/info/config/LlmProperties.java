package cn.edu.ruc.info.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {
    private String baseUrl;
    private String apiToken;
    private String model;
    private String embeddingModel;
    private Integer timeoutMs;
}
