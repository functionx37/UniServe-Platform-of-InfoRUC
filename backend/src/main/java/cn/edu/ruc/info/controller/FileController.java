package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.entity.GeneratedProof;
import cn.edu.ruc.info.service.ApplicationService;
import cn.edu.ruc.info.service.FileStorageService;
import cn.edu.ruc.info.service.ProofGenerationService;
import cn.edu.ruc.info.util.StoragePathHelper;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/files")
public class FileController {

    private final ProofGenerationService proofGenerationService;
    private final ApplicationService applicationService;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;

    public FileController(ProofGenerationService proofGenerationService,
            ApplicationService applicationService,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper) {
        this.proofGenerationService = proofGenerationService;
        this.applicationService = applicationService;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
    }

    @PostMapping("/upload")
    public Result<Map<String, String>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        try {
            FileStorageService.StoredFile storedFile = fileStorageService.saveMultipartFile(
                    file,
                    storagePathHelper.getAttachmentsPath(),
                    "att");
            
            String fileName = storedFile.path().getFileName().toString();
            String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .path("/files/attachments/")
                    .path(fileName)
                    .toUriString();
            
            Map<String, String> data = new HashMap<>();
            data.put("name", storedFile.originalName());
            data.put("url", url);
            data.put("fileName", fileName);
            
            return Result.success(data);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/attachments/{fileName}")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String fileName) {
        Path filePath = storagePathHelper.getAttachmentsPath().resolve(fileName).normalize();
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/templates/admin/{type}")
    public ResponseEntity<Resource> downloadAdminTemplate(@PathVariable String type) {
        String fileName;
        if ("notifications".equalsIgnoreCase(type)) {
            fileName = "notifications_import_template.xlsx";
        } else if ("users".equalsIgnoreCase(type)) {
            fileName = "users_import_template.xlsx";
        } else if ("courses".equalsIgnoreCase(type)) {
            fileName = "courses_import_template.xlsx";
        } else {
            return ResponseEntity.badRequest().build();
        }

        Path templatePath = Paths.get("templates", "01-管理员导入模板", fileName).toAbsolutePath().normalize();
        if (!Files.exists(templatePath)) {
            // 尝试在父目录找（如果程序在 backend 目录下运行）
            templatePath = Paths.get("..", "templates", "01-管理员导入模板", fileName).toAbsolutePath().normalize();
        }
        
        if (!Files.exists(templatePath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(templatePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/templates/student/transcript")
    public ResponseEntity<Resource> downloadStudentTranscriptTemplate() {
        String fileName = "transcript_template.csv";
        Path templatePath = Paths.get("templates", "02-学生下载模板", fileName).toAbsolutePath().normalize();
        if (!Files.exists(templatePath)) {
            templatePath = Paths.get("..", "templates", "02-学生下载模板", fileName).toAbsolutePath().normalize();
        }
        if (!Files.exists(templatePath)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(templatePath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/proofs/{proofId}")
    public ResponseEntity<Resource> downloadProof(@PathVariable String proofId) {
        GeneratedProof proof = proofGenerationService.findById(proofId);
        applicationService.requireVisibleApplication(proof.getApplicationId());
        Long currentUserId = UserContext.getUserId();
        Integer role = UserContext.getRoleId();
        if (currentUserId == null || ((role == null || (role != 1 && role != 2)) && !proof.getUserId().equals(currentUserId))) {
            throw new RuntimeException("无权限下载该证明");
        }
        Resource resource = new FileSystemResource(proof.getFilePath());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(proof.getFileName(), StandardCharsets.UTF_8))
                .body(resource);
    }
}
