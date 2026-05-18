package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.AcademicRecord;
import cn.edu.ruc.info.entity.TranscriptUpload;
import cn.edu.ruc.info.mapper.AcademicRecordMapper;
import cn.edu.ruc.info.mapper.TranscriptUploadMapper;
import cn.edu.ruc.info.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AcademicService {

    @Autowired
    private TranscriptUploadMapper transcriptUploadMapper;
    @Autowired
    private AcademicRecordMapper academicRecordMapper;

    public OverviewVO getOverview() {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        // 最近一次上传
        LambdaQueryWrapper<TranscriptUpload> uploadWrapper = new LambdaQueryWrapper<>();
        uploadWrapper.eq(TranscriptUpload::getUserId, userId).orderByDesc(TranscriptUpload::getUploadedAt)
                .last("limit 1");
        TranscriptUpload upload = transcriptUploadMapper.selectList(uploadWrapper).stream().findFirst().orElse(null);

        // 学分统计
        List<AcademicRecord> records = academicRecordMapper.selectList(
                new LambdaQueryWrapper<AcademicRecord>().eq(AcademicRecord::getUserId, userId));
        double earned = records.stream().mapToDouble(r -> r.getCredits() == null ? 0.0 : r.getCredits().doubleValue())
                .sum();
        double total = 100; // 假设培养方案总学分为100，实际应查询培养方案表（暂无，写死）
        double gap = Math.max(0, total - earned);

        TranscriptInfo transcript = null;
        if (upload != null) {
            transcript = TranscriptInfo.builder()
                    .fileId(upload.getFileId())
                    .fileName(upload.getFileName())
                    .uploadedAt(upload.getUploadedAt() != null ? upload.getUploadedAt().toString() : null)
                    .parsed(upload.getParsed())
                    .build();
        }

        return OverviewVO.builder()
                .transcript(transcript)
                .totalCredits(total)
                .earnedCredits(earned)
                .gapCredits(gap)
                .riskCount(gap > 0 ? 1 : 0)
                .build();
    }

    public AnalysisVO getAnalysis() {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        // 检查是否有已解析的成绩单
        LambdaQueryWrapper<TranscriptUpload> uploadWrapper = new LambdaQueryWrapper<>();
        uploadWrapper.eq(TranscriptUpload::getUserId, userId).eq(TranscriptUpload::getParsed, true)
                .orderByDesc(TranscriptUpload::getUploadedAt).last("limit 1");
        TranscriptUpload upload = transcriptUploadMapper.selectList(uploadWrapper).stream().findFirst().orElse(null);
        if (upload == null) {
            throw new RuntimeException("尚未上传或解析成绩单，请先上传成绩单");
        }

        List<AcademicRecord> records = academicRecordMapper.selectList(
                new LambdaQueryWrapper<AcademicRecord>().eq(AcademicRecord::getUserId, userId));

        // 按类别分组统计
        Map<String, List<AcademicRecord>> groupByCategory = records.stream()
                .collect(Collectors.groupingBy(r -> r.getCategory() != null ? r.getCategory() : "未分类"));

        List<ModuleProgress> modules = new ArrayList<>();
        Map<String, Double> requiredMap = new LinkedHashMap<>(); // 预设各模块学分要求
        requiredMap.put("通识课程", 20.0);
        requiredMap.put("专业必修", 40.0);
        requiredMap.put("专业选修", 30.0);
        requiredMap.put("实践环节", 10.0);

        for (Map.Entry<String, Double> entry : requiredMap.entrySet()) {
            String name = entry.getKey();
            double required = entry.getValue();
            List<AcademicRecord> catRecords = groupByCategory.getOrDefault(name, Collections.emptyList());
            double earned = catRecords.stream()
                    .mapToDouble(r -> r.getCredits() == null ? 0.0 : r.getCredits().doubleValue()).sum();
            double percent = required > 0 ? Math.min(100, Math.round((earned / required) * 100)) : 100;
            double gap = Math.max(0, required - earned);
            modules.add(ModuleProgress.builder()
                    .key(name)
                    .title(name)
                    .requiredCredits(required)
                    .earnedCredits(earned)
                    .percent((int) percent)
                    .gapCredits(gap)
                    .build());
        }

        // 缺失必修课程（示例）
        List<MissingCourse> missing = new ArrayList<>();
        if (records.stream().noneMatch(r -> "数据结构".equals(r.getCourseName()))) {
            missing.add(MissingCourse.builder().course("数据结构").reason("专业必修未完成").build());
        }
        if (records.stream().noneMatch(r -> "操作系统".equals(r.getCourseName()))) {
            missing.add(MissingCourse.builder().course("操作系统").reason("专业必修未完成").build());
        }

        List<String> risks = new ArrayList<>();
        risks.add("学分存在缺口，请及时补修");
        List<String> suggestions = new ArrayList<>();
        suggestions.add("建议优先补齐专业必修课程");
        suggestions.add("实践环节可通过参与科研项目或实习完成");

        return AnalysisVO.builder()
                .transcript(TranscriptInfo.builder()
                        .fileId(upload.getFileId())
                        .fileName(upload.getFileName())
                        .uploadedAt(upload.getUploadedAt().toString())
                        .parsed(true)
                        .build())
                .totalCredits(100.0)
                .earnedCredits(records.stream().mapToDouble(r -> r.getCredits().doubleValue()).sum())
                .gapCredits(100.0 - records.stream().mapToDouble(r -> r.getCredits().doubleValue()).sum())
                .modules(modules)
                .missingRequiredCourses(missing)
                .risks(risks)
                .suggestions(suggestions)
                .build();
    }

    /**
     * 上传成绩单（模拟解析）
     */
    public TranscriptInfo uploadTranscript(MultipartFile file) {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        String fileName = file.getOriginalFilename();
        String fileId = "file-" + UUID.randomUUID().toString().substring(0, 8);

        TranscriptUpload upload = new TranscriptUpload();
        upload.setUserId(userId);
        upload.setFileName(fileName);
        upload.setFileId(fileId);
        upload.setUploadedAt(LocalDateTime.now());
        upload.setParsed(true); // 标记为已解析（实际应异步解析，这里简化）
        transcriptUploadMapper.insert(upload);

        // 模拟解析并插入一些成绩数据（可选）
        // 这里略去，真实场景需要解析PDF/Excel并写入academic_records表

        return TranscriptInfo.builder()
                .fileId(fileId)
                .fileName(fileName)
                .uploadedAt(upload.getUploadedAt().toString())
                .parsed(true)
                .build();
    }

    // 内部 VO
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