package cn.edu.ruc.info.service;

import cn.edu.ruc.info.util.StoragePathHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final StoragePathHelper storagePathHelper;

    public FileStorageService(StoragePathHelper storagePathHelper) {
        this.storagePathHelper = storagePathHelper;
    }

    public void ensureDirectories() {
        createDirectory(storagePathHelper.getRootPath());
        createDirectory(storagePathHelper.getKnowledgeBasePath());
        createDirectory(storagePathHelper.getCurriculumPath());
        createDirectory(storagePathHelper.getTranscriptRootPath());
        createDirectory(storagePathHelper.getProofPath());
    }

    public StoredFile saveMultipartFile(MultipartFile file, Path directory, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件不能为空");
        }
        createDirectory(directory);

        String originalName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload";
        String sanitizedName = sanitizeFileName(originalName);
        String extension = getExtension(sanitizedName);
        String fileName = prefix + "-" + UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = directory.resolve(fileName).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败");
        }

        return new StoredFile(sanitizedName, target);
    }

    public Path resolveKnowledgeDocument(String relativeName) {
        return storagePathHelper.getKnowledgeBasePath().resolve(relativeName).normalize();
    }

    public Path resolveCurriculumFile(String relativeName) {
        return storagePathHelper.getCurriculumPath().resolve(relativeName).normalize();
    }

    public Path resolveTranscriptFile(Long userId, String relativeName) {
        return storagePathHelper.getUserTranscriptPath(userId).resolve(relativeName).normalize();
    }

    public Path resolveProofFile(String relativeName) {
        return storagePathHelper.getProofPath().resolve(relativeName).normalize();
    }

    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new RuntimeException("创建目录失败: " + path);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return fileName.substring(index);
    }

    public record StoredFile(String originalName, Path path) {
    }
}
