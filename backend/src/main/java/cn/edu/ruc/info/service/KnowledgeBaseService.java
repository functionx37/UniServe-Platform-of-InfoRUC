package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.KnowledgeDocument;
import cn.edu.ruc.info.entity.KnowledgeChunkRecord;
import cn.edu.ruc.info.entity.KnowledgeChunkEmbedding;
import cn.edu.ruc.info.mapper.KnowledgeChunkMapper;
import cn.edu.ruc.info.mapper.KnowledgeChunkEmbeddingMapper;
import cn.edu.ruc.info.mapper.KnowledgeDocumentMapper;
import cn.edu.ruc.info.util.StoragePathHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeChunkEmbeddingMapper knowledgeChunkEmbeddingMapper;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final AuditLogService auditLogService;
    private final EmbeddingClientService embeddingClientService;
    private volatile List<KnowledgeChunk> indexedChunks = new ArrayList<>();
    private volatile IndexState indexState = new IndexState(List.of(), Map.of(), 0.0);
    private volatile Map<String, float[]> embeddingIndex = Map.of();

    public KnowledgeBaseService(KnowledgeDocumentMapper knowledgeDocumentMapper,
            KnowledgeChunkMapper knowledgeChunkMapper,
            KnowledgeChunkEmbeddingMapper knowledgeChunkEmbeddingMapper,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            AuditLogService auditLogService,
            EmbeddingClientService embeddingClientService) {
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.knowledgeChunkEmbeddingMapper = knowledgeChunkEmbeddingMapper;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.auditLogService = auditLogService;
        this.embeddingClientService = embeddingClientService;
        loadOrRebuild();
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
            if (knowledgeDocumentMapper.insert(document) <= 0) {
                throw new RuntimeException("插入文档失败");
            }

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

    public BootstrapResult bootstrapFromDirectory(String directory, Long operatorId) {
        try {
            Path dir = resolveBootstrapDirectory(directory);
            List<BootstrapItem> imported = new ArrayList<>();
            List<BootstrapItem> skipped = new ArrayList<>();

            List<Path> files;
            try {
                files = Files.walk(dir)
                        .filter(Files::isRegularFile)
                        .sorted()
                        .collect(Collectors.toList());
            } catch (IOException e) {
                throw new RuntimeException("读取目录失败: " + dir);
            }

            for (Path path : files) {
                String name = path.getFileName() == null ? "" : path.getFileName().toString();
                if (!StringUtils.hasText(name) || !name.contains(".")) {
                    skipped.add(BootstrapItem.builder().file(name).reason("缺少文件名").build());
                    continue;
                }
                String extension = getExtension(name);
                try {
                    validateKnowledgeExtension(extension);
                } catch (RuntimeException e) {
                    skipped.add(BootstrapItem.builder().file(name).reason("不支持的文件类型").build());
                    continue;
                }

                String fileType = extension.replace(".", "").toLowerCase(Locale.ROOT);
                FileStorageService.StoredFile storedFile;
                try {
                    storedFile = fileStorageService.saveLocalFile(path, storagePathHelper.getKnowledgeBasePath(), "kb");
                } catch (RuntimeException e) {
                    skipped.add(BootstrapItem.builder().file(name).reason(e.getMessage()).build());
                    continue;
                }

                Long exists = knowledgeDocumentMapper.selectCount(
                        new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getFileName,
                                storedFile.originalName()));
                if (exists != null && exists > 0) {
                    skipped.add(BootstrapItem.builder().file(storedFile.originalName()).reason("已存在同名文档").build());
                    try {
                        Files.deleteIfExists(storedFile.path());
                    } catch (IOException ignored) {
                    }
                    continue;
                }

                String title = stripExtension(storedFile.originalName());
                String sourceUrl = guessSourceUrl(path, fileType);

                KnowledgeDocument document = new KnowledgeDocument();
                document.setId("kb-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                document.setTitle(title);
                document.setFileName(storedFile.originalName());
                document.setFileType(fileType);
                document.setFilePath(storedFile.path().toString());
                document.setSourceUrl(sourceUrl);
                document.setActive(true);
                document.setUploadedBy(operatorId);
                document.setUploadedAt(LocalDateTime.now());
                knowledgeDocumentMapper.insert(document);
                imported.add(BootstrapItem.builder()
                        .file(storedFile.originalName())
                        .title(title)
                        .documentId(document.getId())
                        .sourceUrl(sourceUrl)
                        .build());
            }

            int chunkCount = 0;
            if (!imported.isEmpty()) {
                chunkCount = rebuildIndex();
            }
            auditLogService.success("BOOTSTRAP_KNOWLEDGE_DIR", dir.toString() + "|" + imported.size());
            return BootstrapResult.builder()
                    .directory(dir.toString())
                    .imported(imported)
                    .skipped(skipped)
                    .chunkCount(chunkCount)
                    .build();
        } catch (RuntimeException e) {
            auditLogService.failure("BOOTSTRAP_KNOWLEDGE_DIR", "knowledge", e.getMessage());
            throw e;
        }
    }

    public DocumentUploadResult updateDocument(String id, UpdateDocumentRequest request) {
        if (!StringUtils.hasText(id)) {
            throw new RuntimeException("缺少 id");
        }
        KnowledgeDocument document = knowledgeDocumentMapper.selectById(id);
        if (document == null) {
            throw new RuntimeException("文档不存在");
        }

        boolean needRebuild = false;
        if (request != null) {
            if (request.getTitle() != null) {
                document.setTitle(request.getTitle().trim());
                needRebuild = true;
            }
            if (request.getSourceUrl() != null) {
                String url = request.getSourceUrl().trim();
                document.setSourceUrl(url.isEmpty() ? null : url);
                needRebuild = true;
            }
            if (request.getActive() != null) {
                document.setActive(request.getActive());
                needRebuild = true;
            }
        }
        knowledgeDocumentMapper.updateById(document);
        if (needRebuild) {
            rebuildIndex();
        }
        return DocumentUploadResult.from(knowledgeDocumentMapper.selectById(id));
    }

    public DocumentUploadResult disableDocument(String id) {
        UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                .active(false)
                .build();
        return updateDocument(id, request);
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
                            .chunkId(buildChunkId(document.getId(), i))
                            .documentId(document.getId())
                            .documentTitle(document.getTitle())
                            .sourceUrl(document.getSourceUrl())
                            .chunkIndex(i)
                            .text(parts.get(i))
                            .build());
                }
            }
            indexedChunks = chunks;
            indexState = buildIndexState(chunks);
            persistChunksIfPossible(documents, chunks);
            persistEmbeddingsIfPossible(chunks);
            embeddingIndex = loadEmbeddings(chunks);
            auditLogService.success("REBUILD_KNOWLEDGE_INDEX", String.valueOf(chunks.size()));
            return chunks.size();
        } catch (RuntimeException e) {
            auditLogService.failure("REBUILD_KNOWLEDGE_INDEX", "knowledge", e.getMessage());
            throw e;
        }
    }

    public List<KnowledgeChunk> search(String question, int limit) {
        String q = question == null ? "" : question.trim();
        if (!StringUtils.hasText(q)) {
            return List.of();
        }

        float[] queryVector = embeddingClientService.embed(q);
        Map<String, float[]> vectors = embeddingIndex;
        if (queryVector != null && vectors != null && !vectors.isEmpty()) {
            List<ScoredChunk> scored = new ArrayList<>();
            for (KnowledgeChunk chunk : indexedChunks) {
                float[] vec = vectors.get(chunk.getChunkId());
                if (vec == null || vec.length != queryVector.length) {
                    continue;
                }
                double score = cosine(queryVector, vec);
                if (score <= 0) {
                    continue;
                }
                scored.add(ScoredChunk.builder()
                        .chunkId(chunk.getChunkId())
                        .documentId(chunk.getDocumentId())
                        .documentTitle(chunk.getDocumentTitle())
                        .sourceUrl(chunk.getSourceUrl())
                        .chunkIndex(chunk.getChunkIndex())
                        .text(chunk.getText())
                        .score(score)
                        .build());
            }
            if (!scored.isEmpty()) {
                return scored.stream()
                        .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                        .limit(limit)
                        .map(item -> KnowledgeChunk.builder()
                                .chunkId(item.getChunkId())
                                .documentId(item.getDocumentId())
                                .documentTitle(item.getDocumentTitle())
                                .sourceUrl(item.getSourceUrl())
                                .chunkIndex(item.getChunkIndex())
                                .text(item.getText())
                                .score(item.getScore())
                                .build())
                        .collect(Collectors.toList());
            }
        }

        IndexState state = indexState;
        List<String> tokens = tokenize(q);
        if (tokens.isEmpty()) {
            return List.of();
        }

        List<ScoredChunk> scored = new ArrayList<>();
        for (int i = 0; i < state.features.size(); i++) {
            ChunkFeatures f = state.features.get(i);
            double score = bm25(tokens, f, state.df, state.avgDl, state.features.size());
            if (score <= 0) {
                continue;
            }
            KnowledgeChunk base = state.chunks.get(i);
            scored.add(ScoredChunk.builder()
                    .chunkId(base.getChunkId())
                    .documentId(base.getDocumentId())
                    .documentTitle(base.getDocumentTitle())
                    .sourceUrl(base.getSourceUrl())
                    .chunkIndex(base.getChunkIndex())
                    .text(base.getText())
                    .score(score)
                    .build());
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(limit)
                .map(item -> KnowledgeChunk.builder()
                        .chunkId(item.getChunkId())
                        .documentId(item.getDocumentId())
                        .documentTitle(item.getDocumentTitle())
                        .sourceUrl(item.getSourceUrl())
                        .chunkIndex(item.getChunkIndex())
                        .text(item.getText())
                        .score(item.getScore())
                        .build())
                .collect(Collectors.toList());
    }

    private void validateKnowledgeExtension(String extension) {
        String normalized = extension.toLowerCase(Locale.ROOT);
        if (!List.of(".pdf", ".txt", ".md", ".docx", ".doc").contains(normalized)) {
            throw new RuntimeException("知识库仅支持 PDF/TXT/MD/DOCX/DOC 文件");
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
            if ("docx".equals(normalizedType)) {
                try (XWPFDocument document = new XWPFDocument(Files.newInputStream(path));
                        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    return extractor.getText();
                }
            }
            if ("doc".equals(normalizedType)) {
                try (HWPFDocument document = new HWPFDocument(Files.newInputStream(path));
                        WordExtractor extractor = new WordExtractor(document)) {
                    return extractor.getText();
                }
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("知识库文档解析失败: " + path.getFileName());
        }
    }

    private Path resolveBootstrapDirectory(String directory) {
        if (StringUtils.hasText(directory)) {
            Path dir = Path.of(directory).toAbsolutePath().normalize();
            if (!Files.exists(dir) || !Files.isDirectory(dir)) {
                throw new RuntimeException("目录不存在: " + dir);
            }
            return dir;
        }

        List<Path> candidates = List.of(
                Path.of("file"),
                Path.of("..", "file"),
                Path.of("..", "..", "file"));
        for (Path candidate : candidates) {
            Path dir = candidate.toAbsolutePath().normalize();
            if (Files.exists(dir) && Files.isDirectory(dir)) {
                return dir;
            }
        }
        throw new RuntimeException("未找到 file 目录");
    }

    private String guessSourceUrl(Path path, String fileType) {
        try {
            String text = extractText(path, fileType);
            return extractFirstUrl(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractFirstUrl(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String url = matcher.group(1);
        if (url == null) {
            return null;
        }
        String cleaned = url.trim();
        while (!cleaned.isEmpty() && (cleaned.endsWith(")") || cleaned.endsWith("]") || cleaned.endsWith("，")
                || cleaned.endsWith("。") || cleaned.endsWith(",") || cleaned.endsWith("."))) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? null : cleaned;
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

    private void loadOrRebuild() {
        try {
            List<KnowledgeDocument> documents = knowledgeDocumentMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getActive, true)
                            .orderByDesc(KnowledgeDocument::getUploadedAt));
            if (documents.isEmpty()) {
                indexedChunks = List.of();
                indexState = new IndexState(List.of(), Map.of(), 0.0);
                embeddingIndex = Map.of();
                return;
            }

            List<String> docIds = documents.stream().map(KnowledgeDocument::getId).toList();
            Map<String, KnowledgeDocument> docMap = documents.stream()
                    .collect(Collectors.toMap(KnowledgeDocument::getId, d -> d, (a, b) -> a));
            List<KnowledgeChunkRecord> records = knowledgeChunkMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunkRecord>().in(KnowledgeChunkRecord::getDocumentId, docIds)
                            .orderByAsc(KnowledgeChunkRecord::getDocumentId)
                            .orderByAsc(KnowledgeChunkRecord::getChunkIndex));

            if (records.isEmpty()) {
                rebuildIndex();
                return;
            }

            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (KnowledgeChunkRecord record : records) {
                KnowledgeDocument doc = docMap.get(record.getDocumentId());
                if (doc == null) {
                    continue;
                }
                chunks.add(KnowledgeChunk.builder()
                        .chunkId(record.getId())
                        .documentId(doc.getId())
                        .documentTitle(doc.getTitle())
                        .sourceUrl(doc.getSourceUrl())
                        .chunkIndex(record.getChunkIndex())
                        .text(record.getContent())
                        .build());
            }
            indexedChunks = chunks;
            indexState = buildIndexState(chunks);
            embeddingIndex = loadEmbeddings(chunks);
        } catch (Exception ignored) {
            try {
                rebuildIndex();
            } catch (Exception ignoredAgain) {
            }
        }
    }

    private void persistChunksIfPossible(List<KnowledgeDocument> documents, List<KnowledgeChunk> chunks) {
        try {
            if (documents == null || documents.isEmpty()) {
                return;
            }
            List<String> docIds = documents.stream().map(KnowledgeDocument::getId).toList();
            knowledgeChunkMapper.delete(
                    new LambdaQueryWrapper<KnowledgeChunkRecord>().in(KnowledgeChunkRecord::getDocumentId, docIds));

            for (KnowledgeChunk chunk : chunks) {
                KnowledgeChunkRecord record = new KnowledgeChunkRecord();
                record.setId(buildChunkId(chunk.getDocumentId(), chunk.getChunkIndex()));
                record.setDocumentId(chunk.getDocumentId());
                record.setChunkIndex(chunk.getChunkIndex());
                record.setContent(chunk.getText());
                knowledgeChunkMapper.insert(record);
            }
        } catch (Exception ignored) {
        }
    }

    private void persistEmbeddingsIfPossible(List<KnowledgeChunk> chunks) {
        try {
            if (chunks == null || chunks.isEmpty()) {
                return;
            }
            float[] probe = embeddingClientService.embed("probe");
            if (probe == null) {
                return;
            }
            List<String> chunkIds = chunks.stream().map(KnowledgeChunk::getChunkId).distinct().toList();
            knowledgeChunkEmbeddingMapper.delete(
                    new LambdaQueryWrapper<KnowledgeChunkEmbedding>().in(KnowledgeChunkEmbedding::getChunkId,
                            chunkIds));
            for (KnowledgeChunk chunk : chunks) {
                float[] vec = embeddingClientService.embed(chunk.getText());
                if (vec == null) {
                    break;
                }
                KnowledgeChunkEmbedding entity = new KnowledgeChunkEmbedding();
                entity.setChunkId(chunk.getChunkId());
                entity.setModel(embeddingClientService.getEmbeddingModel());
                entity.setDim(vec.length);
                entity.setEmbedding(encodeEmbedding(vec));
                knowledgeChunkEmbeddingMapper.insert(entity);
            }
        } catch (Exception ignored) {
        }
    }

    private Map<String, float[]> loadEmbeddings(List<KnowledgeChunk> chunks) {
        try {
            if (chunks == null || chunks.isEmpty()) {
                return Map.of();
            }
            List<String> chunkIds = chunks.stream().map(KnowledgeChunk::getChunkId).distinct().toList();
            List<KnowledgeChunkEmbedding> list = knowledgeChunkEmbeddingMapper.selectList(
                    new LambdaQueryWrapper<KnowledgeChunkEmbedding>().in(KnowledgeChunkEmbedding::getChunkId,
                            chunkIds));
            if (list == null || list.isEmpty()) {
                return Map.of();
            }
            Map<String, float[]> map = new HashMap<>();
            for (KnowledgeChunkEmbedding item : list) {
                float[] vec = decodeEmbedding(item.getEmbedding());
                if (vec != null) {
                    map.put(item.getChunkId(), vec);
                }
            }
            return map;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String buildChunkId(String documentId, Integer chunkIndex) {
        String doc = documentId == null ? "" : documentId;
        int idx = chunkIndex == null ? 0 : chunkIndex;
        String id = doc + "-" + idx;
        return id.length() > 50 ? id.substring(0, 50) : id;
    }

    private String encodeEmbedding(float[] vec) {
        if (vec == null || vec.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        return sb.toString();
    }

    private float[] decodeEmbedding(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        String[] parts = encoded.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vec[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return vec;
    }

    private double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na <= 0 || nb <= 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private IndexState buildIndexState(List<KnowledgeChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new IndexState(List.of(), Map.of(), 0.0);
        }
        List<ChunkFeatures> features = new ArrayList<>(chunks.size());
        Map<String, Integer> df = new HashMap<>();
        long totalLen = 0;

        for (KnowledgeChunk chunk : chunks) {
            Map<String, Integer> tf = new HashMap<>();
            List<String> tokens = tokenize(chunk.getText());
            totalLen += tokens.size();
            for (String token : tokens) {
                tf.put(token, tf.getOrDefault(token, 0) + 1);
            }
            Set<String> uniq = new HashSet<>(tf.keySet());
            for (String token : uniq) {
                df.put(token, df.getOrDefault(token, 0) + 1);
            }
            features.add(new ChunkFeatures(tf, tokens.size()));
        }

        double avgDl = totalLen * 1.0 / chunks.size();
        return new IndexState(chunks, features, df, avgDl);
    }

    private double bm25(List<String> queryTokens, ChunkFeatures chunk, Map<String, Integer> df, double avgDl, int n) {
        double k1 = 1.2;
        double b = 0.75;
        double score = 0.0;
        if (chunk == null || chunk.tf == null || chunk.tf.isEmpty()) {
            return 0.0;
        }
        int dl = Math.max(chunk.length, 1);
        double denomBase = k1 * (1 - b + b * (dl / Math.max(avgDl, 1.0)));

        for (String token : queryTokens) {
            Integer freq = chunk.tf.get(token);
            if (freq == null || freq <= 0) {
                continue;
            }
            int dfi = df.getOrDefault(token, 0);
            double idf = Math.log((n - dfi + 0.5) / (dfi + 0.5) + 1.0);
            score += idf * (freq * (k1 + 1.0)) / (freq + denomBase);
        }
        return score;
    }

    private List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                buf.append(c);
                continue;
            }
            if (buf.length() > 0) {
                tokens.add(buf.toString());
                buf.setLength(0);
            }
            if (isCjk(c)) {
                tokens.add(String.valueOf(c));
            }
        }
        if (buf.length() > 0) {
            tokens.add(buf.toString());
        }

        List<String> enriched = new ArrayList<>(tokens.size() * 2);
        for (String token : tokens) {
            if (token.length() == 1 && token.charAt(0) >= 0x4E00 && token.charAt(0) <= 0x9FFF) {
                enriched.add(token);
                continue;
            }
            if (token.length() >= 2) {
                enriched.add(token);
            }
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            String a = tokens.get(i);
            String b = tokens.get(i + 1);
            if (a.length() == 1 && b.length() == 1 && isCjk(a.charAt(0)) && isCjk(b.charAt(0))) {
                enriched.add(a + b);
            }
        }
        return enriched;
    }

    private boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
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
        private String chunkId;
        private String documentId;
        private String documentTitle;
        private String sourceUrl;
        private Integer chunkIndex;
        private String text;
        private Double score;
    }

    @Data
    @Builder
    public static class UpdateDocumentRequest {
        private String title;
        private String sourceUrl;
        private Boolean active;
    }

    @Data
    @Builder
    public static class DocumentUploadResult {
        private String id;
        private String title;
        private String fileName;
        private String fileType;
        private String sourceUrl;
        private Boolean active;
        private String uploadedAt;

        public static DocumentUploadResult from(KnowledgeDocument document) {
            return DocumentUploadResult.builder()
                    .id(document.getId())
                    .title(document.getTitle())
                    .fileName(document.getFileName())
                    .fileType(document.getFileType())
                    .sourceUrl(document.getSourceUrl())
                    .active(document.getActive())
                    .uploadedAt(document.getUploadedAt() != null ? document.getUploadedAt().toString() : null)
                    .build();
        }
    }

    @Data
    @Builder
    public static class BootstrapResult {
        private String directory;
        private List<BootstrapItem> imported;
        private List<BootstrapItem> skipped;
        private Integer chunkCount;
    }

    @Data
    @Builder
    public static class BootstrapItem {
        private String file;
        private String title;
        private String documentId;
        private String sourceUrl;
        private String reason;
    }

    @Data
    @Builder
    private static class ScoredChunk {
        private String chunkId;
        private String documentId;
        private String documentTitle;
        private String sourceUrl;
        private Integer chunkIndex;
        private String text;
        private Double score;
    }

    private static class ChunkFeatures {
        private final Map<String, Integer> tf;
        private final int length;

        private ChunkFeatures(Map<String, Integer> tf, int length) {
            this.tf = tf;
            this.length = length;
        }
    }

    private static class IndexState {
        private final List<KnowledgeChunk> chunks;
        private final List<ChunkFeatures> features;
        private final Map<String, Integer> df;
        private final double avgDl;

        private IndexState(List<KnowledgeChunk> chunks, Map<String, Integer> df, double avgDl) {
            this(chunks, List.of(), df, avgDl);
        }

        private IndexState(List<KnowledgeChunk> chunks, List<ChunkFeatures> features, Map<String, Integer> df,
                double avgDl) {
            this.chunks = chunks;
            this.features = features;
            this.df = df;
            this.avgDl = avgDl;
        }
    }
}
