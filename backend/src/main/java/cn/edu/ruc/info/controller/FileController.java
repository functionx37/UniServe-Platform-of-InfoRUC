package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.entity.GeneratedProof;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.service.ApplicationService;
import cn.edu.ruc.info.service.FileStorageService;
import cn.edu.ruc.info.service.ProofGenerationService;
import cn.edu.ruc.info.util.StoragePathHelper;
import cn.edu.ruc.info.util.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final ProofGenerationService proofGenerationService;
    private final ApplicationService applicationService;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final UserMapper userMapper;

    public FileController(ProofGenerationService proofGenerationService,
            ApplicationService applicationService,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            UserMapper userMapper) {
        this.proofGenerationService = proofGenerationService;
        this.applicationService = applicationService;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.userMapper = userMapper;
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
            Path sampleCurriculumPath = Paths.get("file", "培养方案示例", "培养方案示例.xlsx").toAbsolutePath().normalize();
            if (!Files.exists(sampleCurriculumPath)) {
                sampleCurriculumPath = Paths.get("..", "file", "培养方案示例", "培养方案示例.xlsx").toAbsolutePath().normalize();
            }
            if (!Files.exists(sampleCurriculumPath)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(sampleCurriculumPath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"培养方案示例.xlsx\"")
                    .body(resource);
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

    @GetMapping("/proofs/preview")
    public ResponseEntity<byte[]> previewProof(
            @RequestParam("typeKey") String typeKey,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "receiver", required = false) String receiver,
            @RequestParam(value = "partyJoinDate", required = false) String partyJoinDate,
            @RequestParam(value = "leaveStart", required = false) String leaveStart,
            @RequestParam(value = "leaveEnd", required = false) String leaveEnd,
            @RequestParam(value = "reason", required = false) String reason) {
        Long userId = null;
        try {
            userId = UserContext.getUserId();
            if (userId == null) {
                return jsonBytes(401, "{\"success\":false,\"message\":\"未登录\"}");
            }
            User user = userMapper.selectById(userId);
            if (user == null) {
                return jsonBytes(404, "{\"success\":false,\"message\":\"用户不存在\"}");
            }
            Map<String, Object> form = new HashMap<>();
            if (purpose != null) {
                form.put("purpose", purpose);
            }
            if (receiver != null) {
                form.put("receiver", receiver);
            }
            if (partyJoinDate != null) {
                form.put("partyJoinDate", partyJoinDate);
            }
            if (leaveStart != null) {
                form.put("leaveStart", leaveStart);
            }
            if (leaveEnd != null) {
                form.put("leaveEnd", leaveEnd);
            }
            if (reason != null) {
                form.put("reason", reason);
            }
            byte[] pdf = proofGenerationService.generatePreview(typeKey, user, form);
            String fileName = "proof_preview.pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                    .body(pdf);
        } catch (Throwable t) {
            log.error("previewProof failed: typeKey={}, userId={}", typeKey, userId, t);
            String msg = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : (": " + t.getMessage()));
            return jsonBytes(500, "{\"success\":false,\"message\":\"" + escapeJson(msg) + "\"}");
        }
    }

    @GetMapping("/proofs/{proofId}")
    public ResponseEntity<Resource> downloadProof(@PathVariable String proofId) {
        try {
            GeneratedProof proof;
            try {
                proof = proofGenerationService.findById(proofId);
            } catch (RuntimeException e) {
                return ResponseEntity.notFound().build();
            }
            try {
                applicationService.requireVisibleApplication(proof.getApplicationId());
            } catch (RuntimeException e) {
                return ResponseEntity.status(403).build();
            }
            Long currentUserId = UserContext.getUserId();
            Integer role = UserContext.getRoleId();
            if (currentUserId == null) {
                return ResponseEntity.status(401).build();
            }
            if ((role == null || (role != 1 && role != 2)) && !proof.getUserId().equals(currentUserId)) {
                return ResponseEntity.status(403).build();
            }
            if (proof.getFilePath() == null || proof.getFilePath().isBlank() || !Files.exists(Path.of(proof.getFilePath()))) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(proof.getFilePath());
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + java.net.URLEncoder.encode(proof.getFileName(), StandardCharsets.UTF_8))
                    .body(resource);
        } catch (Throwable t) {
            log.error("downloadProof failed: proofId={}", proofId, t);
            return ResponseEntity.status(500).build();
        }
    }

    private ResponseEntity<byte[]> jsonBytes(int status, String json) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeJson(String text) {
        String s = String.valueOf(text == null ? "" : text);
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
