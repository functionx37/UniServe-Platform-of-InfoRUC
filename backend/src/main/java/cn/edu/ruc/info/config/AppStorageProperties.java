package cn.edu.ruc.info.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.storage")
public class AppStorageProperties {
    private String root;
    private String knowledgeDir;
    private String curriculumDir;
    private String transcriptDir;
    private String proofDir;
}
