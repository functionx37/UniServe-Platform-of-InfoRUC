package cn.edu.ruc.info.util;

import cn.edu.ruc.info.config.AppStorageProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class StoragePathHelper {

    private final AppStorageProperties storageProperties;

    public StoragePathHelper(AppStorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    public Path getRootPath() {
        return Paths.get(storageProperties.getRoot()).toAbsolutePath().normalize();
    }

    public Path getKnowledgeBasePath() {
        return getRootPath().resolve(storageProperties.getKnowledgeDir()).normalize();
    }

    public Path getCurriculumPath() {
        return getRootPath().resolve(storageProperties.getCurriculumDir()).normalize();
    }

    public Path getTranscriptRootPath() {
        return getRootPath().resolve(storageProperties.getTranscriptDir()).normalize();
    }

    public Path getUserTranscriptPath(Long userId) {
        return getTranscriptRootPath().resolve(String.valueOf(userId)).normalize();
    }

    public Path getProofPath() {
        return getRootPath().resolve(storageProperties.getProofDir()).normalize();
    }
}
