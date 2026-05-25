package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.entity.GeneratedProof;
import cn.edu.ruc.info.entity.Application;
import cn.edu.ruc.info.service.ApplicationService;
import cn.edu.ruc.info.service.ProofGenerationService;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/files")
public class FileController {

    private final ProofGenerationService proofGenerationService;
    private final ApplicationService applicationService;

    public FileController(ProofGenerationService proofGenerationService, ApplicationService applicationService) {
        this.proofGenerationService = proofGenerationService;
        this.applicationService = applicationService;
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
