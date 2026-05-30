package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.AcademicRecord;
import cn.edu.ruc.info.entity.TranscriptUpload;
import cn.edu.ruc.info.mapper.AcademicRecordMapper;
import cn.edu.ruc.info.mapper.TranscriptUploadMapper;
import cn.edu.ruc.info.util.StoragePathHelper;
import cn.edu.ruc.info.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AcademicService {

    private final TranscriptUploadMapper transcriptUploadMapper;
    private final AcademicRecordMapper academicRecordMapper;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final TranscriptParsingService transcriptParsingService;
    private final CurriculumService curriculumService;

    public AcademicService(TranscriptUploadMapper transcriptUploadMapper,
            AcademicRecordMapper academicRecordMapper,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            TranscriptParsingService transcriptParsingService,
            CurriculumService curriculumService) {
        this.transcriptUploadMapper = transcriptUploadMapper;
        this.academicRecordMapper = academicRecordMapper;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.transcriptParsingService = transcriptParsingService;
        this.curriculumService = curriculumService;
    }

    public OverviewVO getOverview() {
        Long userId = requireUserId();
        TranscriptUpload upload = getLatestUpload(userId, null);
        List<AcademicRecord> records = listUserRecords(userId);

        CurriculumService.CurriculumDefinition definition = null;
        try {
            definition = curriculumService.getActiveDefinition();
        } catch (RuntimeException ignored) {
        }

        double earnedCredits = records.stream()
                .mapToDouble(record -> record.getCredits() == null ? 0.0 : record.getCredits().doubleValue())
                .sum();
        double totalCredits = definition == null ? 0.0
                : definition.getRequiredModules().stream()
                        .mapToDouble(module -> module.getRequiredCredits() == null ? 0.0 : module.getRequiredCredits())
                        .sum();
        double gapCredits = Math.max(0, totalCredits - earnedCredits);
        int riskCount = (gapCredits > 0 ? 1 : 0) + (!records.isEmpty() && definition != null
                ? countMissingRequiredCourses(records, definition).size() > 0 ? 1 : 0
                : 0);

        return OverviewVO.builder()
                .transcript(upload == null ? null : toTranscriptInfo(upload))
                .totalCredits(totalCredits)
                .earnedCredits(earnedCredits)
                .gapCredits(gapCredits)
                .riskCount(riskCount)
                .build();
    }

    public AnalysisVO getAnalysis() {
        Long userId = requireUserId();
        TranscriptUpload upload = getLatestUpload(userId, true);
        if (upload == null) {
            throw new RuntimeException("尚未上传或解析成绩单，请先上传成绩单");
        }

        List<AcademicRecord> records = listUserRecords(userId);
        if (records.isEmpty()) {
            throw new RuntimeException("成绩单解析成功，但未识别到课程数据");
        }
        CurriculumService.CurriculumDefinition definition = curriculumService.getActiveDefinition();

        Map<String, AcademicRecord> courseIndex = records.stream()
                .filter(record -> StringUtils.hasText(record.getCourseName()))
                .collect(Collectors.toMap(
                        record -> normalize(record.getCourseName()),
                        record -> record,
                        (left, right) -> left));

        List<ModuleProgress> modules = definition.getRequiredModules().stream()
                .map(module -> {
                    double earned = records.stream()
                            .map(record -> enrichCategory(record, definition))
                            .filter(record -> module.getTitle().equals(record.getCategory()))
                            .mapToDouble(record -> record.getCredits() == null ? 0.0 : record.getCredits().doubleValue())
                            .sum();
                    double required = module.getRequiredCredits() == null ? 0.0 : module.getRequiredCredits();
                    int percent = required <= 0 ? 100 : (int) Math.min(100, Math.round(earned * 100 / required));
                    return ModuleProgress.builder()
                            .key(module.getKey())
                            .title(module.getTitle())
                            .requiredCredits(required)
                            .earnedCredits(earned)
                            .percent(percent)
                            .gapCredits(Math.max(0, required - earned))
                            .build();
                })
                .collect(Collectors.toList());

        List<MissingCourse> missingCourses = countMissingRequiredCourses(records, definition);
        double totalCredits = definition.getRequiredModules().stream()
                .mapToDouble(module -> module.getRequiredCredits() == null ? 0.0 : module.getRequiredCredits())
                .sum();
        double earnedCredits = records.stream()
                .mapToDouble(record -> record.getCredits() == null ? 0.0 : record.getCredits().doubleValue())
                .sum();
        double gapCredits = Math.max(0, totalCredits - earnedCredits);

        List<String> risks = new ArrayList<>();
        if (gapCredits > 0) {
            risks.add("总学分仍存在 " + ((int) Math.ceil(gapCredits)) + " 学分缺口");
        }
        if (!missingCourses.isEmpty()) {
            risks.add("仍有未完成必修课程：" + missingCourses.stream()
                    .map(MissingCourse::getCourse)
                    .collect(Collectors.joining("、")));
        }
        if (risks.isEmpty()) {
            risks.add("当前未发现明显学业风险");
        }

        List<String> suggestions = buildSuggestions(modules, missingCourses, gapCredits);

        return AnalysisVO.builder()
                .transcript(toTranscriptInfo(upload))
                .totalCredits(totalCredits)
                .earnedCredits(earnedCredits)
                .gapCredits(gapCredits)
                .modules(modules)
                .missingRequiredCourses(missingCourses)
                .risks(risks)
                .suggestions(suggestions)
                .build();
    }

    @Transactional
    public TranscriptInfo uploadTranscript(MultipartFile file) {
        Long userId = requireUserId();
        String fileName = file.getOriginalFilename();
        String extension = extensionOf(fileName);
        if (!List.of(".pdf", ".xls", ".xlsx", ".csv").contains(extension)) {
            throw new RuntimeException("仅支持 PDF / Excel 成绩单文件");
        }

        String fileId = "file-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        FileStorageService.StoredFile storedFile = fileStorageService.saveMultipartFile(
                file,
                storagePathHelper.getUserTranscriptPath(userId),
                "transcript");

        TranscriptUpload upload = new TranscriptUpload();
        upload.setUserId(userId);
        upload.setFileName(storedFile.originalName());
        upload.setFileId(fileId);
        upload.setFilePath(storedFile.path().toString());
        upload.setUploadedAt(LocalDateTime.now());
        upload.setParsed(false);
        transcriptUploadMapper.insert(upload);

        try {
            TranscriptParsingService.ParseResult parseResult = transcriptParsingService.parse(
                    storedFile.path(),
                    storedFile.originalName(),
                    userId);
            academicRecordMapper.delete(new LambdaQueryWrapper<AcademicRecord>().eq(AcademicRecord::getUserId, userId));
            parseResult.getRecords().forEach(academicRecordMapper::insert);

            upload.setParsed(true);
            upload.setParseMessage(parseResult.getMessage());
            transcriptUploadMapper.updateById(upload);

            return TranscriptInfo.builder()
                    .fileId(upload.getFileId())
                    .fileName(upload.getFileName())
                    .uploadedAt(upload.getUploadedAt().toString())
                    .parsed(true)
                    .build();
        } catch (RuntimeException e) {
            upload.setParsed(false);
            upload.setParseMessage(e.getMessage());
            transcriptUploadMapper.updateById(upload);
            throw e;
        }
    }

    private List<AcademicRecord> listUserRecords(Long userId) {
        return academicRecordMapper.selectList(new LambdaQueryWrapper<AcademicRecord>().eq(AcademicRecord::getUserId, userId));
    }

    private TranscriptUpload getLatestUpload(Long userId, Boolean parsed) {
        LambdaQueryWrapper<TranscriptUpload> wrapper = new LambdaQueryWrapper<TranscriptUpload>()
                .eq(TranscriptUpload::getUserId, userId)
                .orderByDesc(TranscriptUpload::getUploadedAt)
                .last("limit 1");
        if (parsed != null) {
            wrapper.eq(TranscriptUpload::getParsed, parsed);
        }
        return transcriptUploadMapper.selectList(wrapper).stream().findFirst().orElse(null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        return userId;
    }

    private TranscriptInfo toTranscriptInfo(TranscriptUpload upload) {
        return TranscriptInfo.builder()
                .fileId(upload.getFileId())
                .fileName(upload.getFileName())
                .uploadedAt(upload.getUploadedAt() == null ? null : upload.getUploadedAt().toString())
                .parsed(upload.getParsed())
                .build();
    }

    private AcademicRecord enrichCategory(AcademicRecord record, CurriculumService.CurriculumDefinition definition) {
        if (StringUtils.hasText(record.getCategory()) && !"未分类".equals(record.getCategory())) {
            return record;
        }
        String normalized = normalize(record.getCourseName());
        definition.getRequiredCourses().stream()
                .filter(course -> normalize(course.getCourseName()).equals(normalized))
                .findFirst()
                .ifPresent(course -> record.setCategory(course.getModule()));
        if (!StringUtils.hasText(record.getCategory())) {
            record.setCategory("未分类");
        }
        return record;
    }

    private List<MissingCourse> countMissingRequiredCourses(List<AcademicRecord> records,
            CurriculumService.CurriculumDefinition definition) {
        Set<String> learned = records.stream()
                .map(AcademicRecord::getCourseName)
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .collect(Collectors.toSet());
        return definition.getRequiredCourses().stream()
                .filter(course -> Boolean.TRUE.equals(course.getRequired()))
                .filter(course -> !learned.contains(normalize(course.getCourseName())))
                .map(course -> MissingCourse.builder()
                        .course(course.getCourseName())
                        .reason(course.getModule() + " 必修未完成")
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> buildSuggestions(List<ModuleProgress> modules, List<MissingCourse> missingCourses, double gapCredits) {
        List<String> suggestions = new ArrayList<>();
        if (!missingCourses.isEmpty()) {
            suggestions.add("优先补齐必修课程：" + missingCourses.stream()
                    .limit(3)
                    .map(MissingCourse::getCourse)
                    .collect(Collectors.joining("、")));
        }
        modules.stream()
                .filter(module -> module.getGapCredits() > 0)
                .sorted(Comparator.comparingDouble(ModuleProgress::getGapCredits).reversed())
                .findFirst()
                .ifPresent(module -> suggestions.add("当前模块缺口最大的是“" + module.getTitle() + "”，建议下学期优先补足相关课程"));
        if (gapCredits > 0) {
            suggestions.add("选课时优先覆盖缺口模块，避免将补修压力集中到毕业前");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("当前培养方案匹配情况良好，建议继续按计划完成后续课程");
        }
        return suggestions;
    }

    private String normalize(String input) {
        return input == null ? "" : input.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new RuntimeException("文件缺少后缀名");
        }
        return fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    @Data
    @Builder
    public static class OverviewVO {
        private TranscriptInfo transcript;
        private double totalCredits;
        private double earnedCredits;
        private double gapCredits;
        private int riskCount;
    }

    @Data
    @Builder
    public static class AnalysisVO {
        private TranscriptInfo transcript;
        private double totalCredits;
        private double earnedCredits;
        private double gapCredits;
        private List<ModuleProgress> modules;
        private List<MissingCourse> missingRequiredCourses;
        private List<String> risks;
        private List<String> suggestions;
    }

    @Data
    @Builder
    public static class TranscriptInfo {
        private String fileId;
        private String fileName;
        private String uploadedAt;
        private Boolean parsed;
    }

    @Data
    @Builder
    public static class ModuleProgress {
        private String key;
        private String title;
        private double requiredCredits;
        private double earnedCredits;
        private int percent;
        private double gapCredits;
    }

    @Data
    @Builder
    public static class MissingCourse {
        private String course;
        private String reason;
    }
}
