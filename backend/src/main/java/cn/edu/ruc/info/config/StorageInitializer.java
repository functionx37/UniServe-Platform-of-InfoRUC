package cn.edu.ruc.info.config;

import cn.edu.ruc.info.service.FileStorageService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StorageInitializer implements ApplicationRunner {

    private final FileStorageService fileStorageService;

    public StorageInitializer(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void run(ApplicationArguments args) {
        fileStorageService.ensureDirectories();
    }
}
