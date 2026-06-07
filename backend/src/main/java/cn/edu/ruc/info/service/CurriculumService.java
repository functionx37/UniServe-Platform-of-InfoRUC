package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.CurriculumFile;
import cn.edu.ruc.info.entity.ImportSession;
import cn.edu.ruc.info.mapper.CurriculumFileMapper;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.util.StoragePathHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CurriculumService {

    private final CurriculumFileMapper curriculumFileMapper;
    private final ImportSessionMapper importSessionMapper;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final CurriculumDefinitionParser curriculumDefinitionParser;
    private final AuditLogService auditLogService;
    private volatile CurriculumDefinition cachedDefinition;
    private static final java.time.format.DateTimeFormatter FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public CurriculumService(CurriculumFileMapper curriculumFileMapper,
            ImportSessionMapper importSessionMapper,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            CurriculumDefinitionParser curriculumDefinitionParser,
            AuditLogService auditLogService) {
        this.curriculumFileMapper = curriculumFileMapper;
        this.importSessionMapper = importSessionMapper;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.curriculumDefinitionParser = curriculumDefinitionParser;
        this.auditLogService = auditLogService;
    }

    public UploadResult upload(MultipartFile file, Long operatorId) {
        try {
            String originalName = file.getOriginalFilename();
            String extension = getExtension(originalName);
            if (!List.of(".xlsx", ".xls").contains(extension)) {
                throw new RuntimeException("培养方案仅支持 Excel 文件 (.xlsx, .xls)");
            }

            FileStorageService.StoredFile storedFile = fileStorageService.saveMultipartFile(
                    file,
                    storagePathHelper.getCurriculumPath(),
                    "curriculum");
            Path actualPath = storedFile.path();

            CurriculumDefinition definition = curriculumDefinitionParser.parseExcelDefinition(actualPath);
            curriculumDefinitionParser.writeProcessedDefinition(actualPath, definition);
            cachedDefinition = definition;

            curriculumFileMapper.selectList(null).forEach(item -> {
                item.setActive(false);
                curriculumFileMapper.updateById(item);
            });

            CurriculumFile curriculumFile = new CurriculumFile();
            curriculumFile.setId("cur-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            curriculumFile.setFileName(storedFile.originalName());
            curriculumFile.setFileType(extension.replace(".", ""));
            curriculumFile.setFilePath(actualPath.toString());
            curriculumFile.setVersion(definition.getVersion());
            curriculumFile.setActive(true);
            curriculumFile.setUploadedBy(operatorId);
            curriculumFile.setUploadedAt(LocalDateTime.now());
            curriculumFileMapper.insert(curriculumFile);

            // 同步创建一条导入记录，以便仪表盘显示
            ImportSession session = new ImportSession();
            session.setId("curimp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            session.setFileName(originalName);
            session.setTotalRows(definition.getRequiredCourses().size());
            session.setSuccessRows(definition.getRequiredCourses().size());
            session.setFailedRows(0);
            session.setImportedAt(LocalDateTime.now().format(FORMATTER));
            session.setOperatorId(operatorId);
            importSessionMapper.insert(session);

            auditLogService.success("UPLOAD_CURRICULUM", curriculumFile.getId());
            return UploadResult.builder()
                    .id(curriculumFile.getId())
                    .fileName(curriculumFile.getFileName())
                    .version(curriculumFile.getVersion())
                    .programName(definition.getProgramName())
                    .requiredModules(definition.getRequiredModules().size())
                    .requiredCourses(definition.getRequiredCourses().size())
                    .requirementGroups(definition.getRequirementGroups().size())
                    .preciseCredits(definition.getPreciseCredits())
                    .metricLabel(definition.getMetricLabel())
                    .processedFileName(curriculumDefinitionParser.getProcessedPath(actualPath).getFileName().toString())
                    .uploadedAt(curriculumFile.getUploadedAt().toString())
                    .build();
        } catch (RuntimeException e) {
            auditLogService.failure("UPLOAD_CURRICULUM", "curriculum", e.getMessage());
            throw e;
        }
    }

    public UploadResult getLatestSummary() {
        CurriculumFile latest = curriculumFileMapper.selectOne(
                new LambdaQueryWrapper<CurriculumFile>().eq(CurriculumFile::getActive, true)
                        .orderByDesc(CurriculumFile::getUploadedAt).last("limit 1"));
        if (latest == null) {
            throw new RuntimeException("尚未上传培养方案");
        }
        CurriculumDefinition definition = getActiveDefinition();
        return UploadResult.builder()
                .id(latest.getId())
                .fileName(latest.getFileName())
                .version(latest.getVersion())
                .programName(definition.getProgramName())
                .requiredModules(definition.getRequiredModules().size())
                .requiredCourses(definition.getRequiredCourses().size())
                .requirementGroups(definition.getRequirementGroups().size())
                .preciseCredits(definition.getPreciseCredits())
                .metricLabel(definition.getMetricLabel())
                .processedFileName(curriculumDefinitionParser.getProcessedPath(Path.of(latest.getFilePath())).getFileName().toString())
                .uploadedAt(latest.getUploadedAt() != null ? latest.getUploadedAt().toString() : null)
                .build();
    }

    public boolean delete(String id) {
        CurriculumFile file = curriculumFileMapper.selectById(id);
        if (file == null) return false;
        
        fileStorageService.deleteFile(file.getFilePath());
        fileStorageService.deleteFile(curriculumDefinitionParser.getProcessedPath(Path.of(file.getFilePath())).toString());
        curriculumFileMapper.deleteById(id);
        if (file.getActive()) {
            cachedDefinition = null;
        }
        auditLogService.success("DELETE_CURRICULUM", id);
        return true;
    }

    public CurriculumDefinition getActiveDefinition() {
        if (cachedDefinition != null) {
            return cachedDefinition;
        }
        CurriculumFile latest = curriculumFileMapper.selectOne(
                new LambdaQueryWrapper<CurriculumFile>().eq(CurriculumFile::getActive, true)
                        .orderByDesc(CurriculumFile::getUploadedAt).last("limit 1"));
        if (latest == null) {
            throw new RuntimeException("尚未上传培养方案");
        }
        cachedDefinition = curriculumDefinitionParser.loadOrParse(Path.of(latest.getFilePath()));
        return cachedDefinition;
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new RuntimeException("文件缺少后缀名");
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    @Data
    @Builder
    public static class CurriculumDefinition {
        private String programName;
        private String version;
        private Boolean preciseCredits;
        private String metricLabel;
        private List<RequiredModule> requiredModules;
        private List<RequirementGroup> requirementGroups;
        private List<RequiredCourse> requiredCourses;
    }

    @Data
    @Builder
    public static class RequiredModule {
        private String key;
        private String title;
        private Double requiredCredits;
        private Integer requirementGroupCount;
        private Integer courseEntryCount;
    }

    @Data
    @Builder
    public static class RequirementGroup {
        private String key;
        private String moduleKey;
        private String moduleTitle;
        private String ruleText;
        private String ruleType;
        private Integer requiredCount;
        private Integer candidateCount;
        private Double requiredCredits;
        private List<RequiredCourse> courses;
    }

    @Data
    @Builder
    public static class RequiredCourse {
        private String courseName;
        private String module;
        private String moduleKey;
        private String groupKey;
        private Double credits;
        private Boolean required;
        private String ruleText;
        private String ruleType;
        private Integer groupRequiredCount;
        private String offeredTerm;
        private List<String> offeredTermCodes;
        private String normalizedName;
    }

    @Data
    @Builder
    public static class UploadResult {
        private String id;
        private String fileName;
        private String version;
        private String programName;
        private Integer requiredModules;
        private Integer requiredCourses;
        private Integer requirementGroups;
        private Boolean preciseCredits;
        private String metricLabel;
        private String processedFileName;
        private String uploadedAt;
    }
}
