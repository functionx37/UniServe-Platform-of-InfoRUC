package cn.edu.ruc.info.config;

import cn.edu.ruc.info.util.EncryptUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UtilConfig {

    @Value("${app.security.aes-key}")
    private String aesKey;

    @Bean
    public EncryptUtil encryptUtil() {
        return new EncryptUtil(aesKey);
    }
}