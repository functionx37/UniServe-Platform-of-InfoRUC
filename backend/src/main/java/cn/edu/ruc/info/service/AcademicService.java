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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AcademicService {

    private final TranscriptUploadMapper transcriptUploadMapper;
    private final AcademicRecordMapper academicRecordMapper;
    private final FileStorageService fileStorageService;
    private final StoragePathHelper storagePathHelper;
    private final TranscriptParsingService transcriptParsingService;
    private final CurriculumService curriculumService;
    private final AcademicAnalysisEngine academicAnalysisEngine;

    public AcademicService(TranscriptUploadMapper transcriptUploadMapper,
            AcademicRecordMapper academicRecordMapper,
            FileStorageService fileStorageService,
            StoragePathHelper storagePathHelper,
            TranscriptParsingService transcriptParsingService,
            CurriculumService curriculumService,
            AcademicAnalysisEngine academicAnalysisEngine) {
        this.transcriptUploadMapper = transcriptUploadMapper;
        this.academicRecordMapper = academicRecordMapper;
        this.fileStorageService = fileStorageService;
        this.storagePathHelper = storagePathHelper;
        this.transcriptParsingService = transcriptParsingService;
        this.curriculumService = curriculumService;
        this.academicAnalysisEngine = academicAnalysisEngine;
    }

    public OverviewVO getOverview() {
        Long userId = requireUserId();
        TranscriptUpload upload = getLatestUpload(userId, null);
        CurriculumService.CurriculumDefinition definition = null;
        AcademicAnalysisEngine.AnalysisSnapshot snapshot = null;

        try {
            definition = curriculumService.getActiveDefinition();
            List<AcademicRecord> records = academicAnalysisEngine.enrichRecords(listUserRecords(userId), definition);
            if (upload != null && Boolean.TRUE.equals(upload.getParsed()) && !records.isEmpty()) {
                snapshot = academicAnalysisEngine.analyze(records, definition, LocalDate.now());
            }
        } catch (RuntimeException ignored) {
        }

        int riskCount = snapshot == null ? 0 : Math.max(0, snapshot.getRisks().size() - 1);
        return OverviewVO.builder()
                .transcript(upload == null ? null : toTranscriptInfo(upload))
                .totalCredits(snapshot == null ? 0.0 : snapshot.getTotalCredits())
                .earnedCredits(snapshot == null ? 0.0 : snapshot.getEarnedCredits())
                .gapCredits(snapshot == null ? 0.0 : snapshot.getGapCredits())
                .riskCount(riskCount)
                .metricLabel(definition == null ? "学分" : defaultMetricLabel(definition))
                .preciseCredits(definition != null && Boolean.TRUE.equals(definition.getPreciseCredits()))
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
        academicAnalysisEngine.enrichRecords(records, definition);
        AcademicAnalysisEngine.AnalysisSnapshot snapshot = academicAnalysisEngine.analyze(records, definition, LocalDate.now());

        return AnalysisVO.builder()
                .transcript(toTranscriptInfo(upload))
                .metricLabel(snapshot.getMetricLabel())
                .preciseCredits(snapshot.isPreciseCredits())
                .totalCredits(snapshot.getTotalCredits())
                .earnedCredits(snapshot.getEarnedCredits())
                .gapCredits(snapshot.getGapCredits())
                .modules(snapshot.getModules().stream()
                        .map(item -> ModuleProgress.builder()
                                .key(item.getKey())
                                .title(item.getTitle())
                                .requiredCredits(item.getRequiredCredits())
                                .earnedCredits(item.getEarnedCredits())
                                .percent(item.getPercent())
                                .gapCredits(item.getGapCredits())
                                .build())
                        .toList())
                .missingRequiredCourses(snapshot.getMissingRequiredCourses().stream()
                        .map(item -> MissingCourse.builder()
                                .course(item.getCourse())
                                .reason(item.getReason())
                                .module(item.getModule())
                                .offeredTerm(item.getOfferedTerm())
                                .availableThisTerm(item.isAvailableThisTerm())
                                .build())
                        .toList())
                .unfinishedGroups(snapshot.getUnfinishedGroups())
                .recommendedCourses(snapshot.getRecommendedCourses())
                .semesterContext(snapshot.getSemesterContext())
                .matchedCourseCount(snapshot.getMatchedCourseCount())
                .unmatchedTranscriptCourses(snapshot.getUnmatchedTranscriptCourses())
                .risks(snapshot.getRisks())
                .suggestions(snapshot.getSuggestions())
                .build();
    }

    @Transactional
    public TranscriptInfo uploadTranscript(MultipartFile file) {
        Long userId = requireUserId();
        String fileName = file.getOriginalFilename();
        String extension = extensionOf(fileName);
        if (!List.of(".pdf", ".xls", ".xlsx", ".csv").contains(extension)) {
            throw new RuntimeException("仅支持 PDF / Excel / CSV 成绩单文件");
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
            CurriculumService.CurriculumDefinition definition = curriculumService.getActiveDefinition();
            TranscriptParsingService.ParseResult parseResult = transcriptParsingService.parse(
                    storedFile.path(),
                    storedFile.originalName(),
                    userId);
            List<AcademicRecord> records = academicAnalysisEngine.enrichRecords(parseResult.getRecords(), definition);
            academicRecordMapper.delete(new LambdaQueryWrapper<AcademicRecord>().eq(AcademicRecord::getUserId, userId));
            records.forEach(academicRecordMapper::insert);

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
        return academicRecordMapper.selectList(
                new LambdaQueryWrapper<AcademicRecord>()
                        .eq(AcademicRecord::getUserId, userId));
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

    private String defaultMetricLabel(CurriculumService.CurriculumDefinition definition) {
        return StringUtils.hasText(definition.getMetricLabel()) ? definition.getMetricLabel() : "学分";
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
        private String metricLabel;
        private boolean preciseCredits;
    }

    @Data
    @Builder
    public static class AnalysisVO {
        private TranscriptInfo transcript;
        private String metricLabel;
        private boolean preciseCredits;
        private double totalCredits;
        private double earnedCredits;
        private double gapCredits;
        private List<ModuleProgress> modules;
        private List<MissingCourse> missingRequiredCourses;
        private List<AcademicAnalysisEngine.UnfinishedGroupItem> unfinishedGroups;
        private List<AcademicAnalysisEngine.RecommendationItem> recommendedCourses;
        private AcademicAnalysisEngine.SemesterContext semesterContext;
        private int matchedCourseCount;
        private List<String> unmatchedTranscriptCourses;
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
        private String module;
        private String offeredTerm;
        private boolean availableThisTerm;
    }
}
