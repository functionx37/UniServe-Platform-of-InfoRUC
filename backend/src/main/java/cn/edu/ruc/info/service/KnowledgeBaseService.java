package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.KnowledgeDocument;
import cn.edu.ruc.info.mapper.KnowledgeDocumentMapper;
import cn.edu.ruc.info.util.StoragePathHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final AuditLogService auditLogService;
    private volatile List<KnowledgeChunk> indexedChunks = new ArrayList<>();

    public KnowledgeBaseService(KnowledgeDocumentMapper knowledgeDocumentMapper,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            AuditLogService auditLogService) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.auditLogService = auditLogService;
    }

    public DocumentUploadResult uploadDocument(MultipartFile file, String title, String sourceUrl, Long operatorId) {
        try {
            String originalName = file.getOriginalFilename();
            String extension = getExtension(originalName);
            validateKnowledgeExtension(extension);

            FileStorageService.StoredFile storedFile = fileStorageService.saveMultipartFile(
                    file,
                    storagePathHelper.getKnowledgeBasePath(),
                    "kb");

            KnowledgeDocument document = new KnowledgeDocument();
            document.setId("kb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            document.setTitle(StringUtils.hasText(title) ? title.trim() : stripExtension(storedFile.originalName()));
            document.setFileName(storedFile.originalName());
            document.setFileType(extension.replace(".", "").toLowerCase(Locale.ROOT));
            document.setFilePath(storedFile.path().toString());
            document.setSourceUrl(StringUtils.hasText(sourceUrl) ? sourceUrl.trim() : null);
            document.setActive(true);
            document.setUploadedBy(operatorId);
            document.setUploadedAt(LocalDateTime.now());
            knowledgeDocumentMapper.insert(document);

            rebuildIndex();
            auditLogService.success("UPLOAD_KNOWLEDGE_DOC", document.getId());
            return DocumentUploadResult.from(document);
        } catch (RuntimeException e) {
            auditLogService.failure("UPLOAD_KNOWLEDGE_DOC", "knowledge", e.getMessage());
            throw e;
        }
    }

    public List<DocumentUploadResult> listDocuments() {
        return knowledgeDocumentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().orderByDesc(KnowledgeDocument::getUploadedAt)).stream()
                .map(DocumentUploadResult::from)
                .collect(Collectors.toList());
    }

    public int rebuildIndex() {
        try {
            List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getActive, true)
                            .orderByDesc(KnowledgeDocument::getUploadedAt));
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (KnowledgeDocument document : documents) {
                String text = extractText(Path.of(document.getFilePath()), document.getFileType());
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                List<String> parts = splitIntoChunks(text);
                for (int i = 0; i < parts.size(); i++) {
                    chunks.add(KnowledgeChunk.builder()
                            .documentId(document.getId())
                            .documentTitle(document.getTitle())
                            .sourceUrl(document.getSourceUrl())
                            .chunkIndex(i)
                            .text(parts.get(i))
                            .build());
                }
            }
            indexedChunks = chunks;
            auditLogService.success("REBUILD_KNOWLEDGE_INDEX", String.valueOf(chunks.size()));
            return chunks.size();
        } catch (RuntimeException e) {
            auditLogService.failure("REBUILD_KNOWLEDGE_INDEX", "knowledge", e.getMessage());
            throw e;
        }
    }

    public List<KnowledgeChunk> search(String question, int limit) {
        String normalized = normalize(question);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return indexedChunks.stream()
                .map(chunk -> RankedChunk.builder()
                        .chunk(chunk)
                        .score(scoreChunk(normalized, chunk.getText()))
                        .build())
                .filter(item -> item.getScore() > 0)
                .sorted(Comparator.comparingInt(RankedChunk::getScore).reversed())
                .limit(limit)
                .map(RankedChunk::getChunk)
                .collect(Collectors.toList());
    }

    private void validateKnowledgeExtension(String extension) {
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (!List.of(".pdf", ".txt", ".md").contains(normalized)) {
            throw new RuntimeException("知识库仅支持 PDF/TXT/MD 文件");
        }
    }

    private String extractText(Path path, String fileType) {
        String normalizedType = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        try {
            if ("pdf".equals(normalizedType)) {
                try (PDDocument document = Loader.loadPDF(path.toFile())) {
                    return new PDFTextStripper().getText(document);
                }
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("知识库文档解析失败: " + path.getFileName());
        }
    }

    private List<String> splitIntoChunks(String text) {
        String compact = text.replace("\r", "\n").replaceAll("\n{2,}", "\n").trim();
        if (!StringUtils.hasText(compact)) {
            return List.of();
        }
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : compact.split("\n")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (current.length() + trimmed.length() > 500 && current.length() > 0) {
                paragraphs.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(trimmed).append('\n');
        }
        if (current.length() > 0) {
            paragraphs.add(current.toString().trim());
        }
        return paragraphs;
    }

    private String normalize(String input) {
        return input == null ? "" : input.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private int scoreChunk(String normalizedQuestion, String chunkText) {
        String normalizedChunk = normalize(chunkText);
        int score = 0;
        for (int i = 0; i < normalizedQuestion.length(); i++) {
            for (int j = i + 1; j <= Math.min(normalizedQuestion.length(), i + 6); j++) {
                String token = normalizedQuestion.substring(i, j);
                if (token.length() < 2) {
                    continue;
                }
                if (normalizedChunk.contains(token)) {
                    score += token.length() * token.length();
                }
            }
        }
        return score;
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new RuntimeException("文件缺少后缀名");
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

    @Data
    @Builder
    public static class KnowledgeChunk {
        private String documentId;
        private String documentTitle;
        private String sourceUrl;
        private Integer chunkIndex;
        private String text;
    }

    @Data
    @Builder
    private static class RankedChunk {
        private KnowledgeChunk chunk;
        private int score;
    }

    @Data
    @Builder
    public static class DocumentUploadResult {
        private String id;
        private String title;
        private String fileName;
        private String fileType;
        private String sourceUrl;
        private String uploadedAt;

        public static DocumentUploadResult from(KnowledgeDocument document) {
            return DocumentUploadResult.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .fileName(document.getFileName())
                    .fileType(document.getFileType())
                    .sourceUrl(document.getSourceUrl())
                    .uploadedAt(document.getUploadedAt() != null ? document.getUploadedAt().toString() : null)
                    .build();
        }
    }
}
