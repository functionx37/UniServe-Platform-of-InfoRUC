package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.ImportSessionVO;
import cn.edu.ruc.info.entity.ImportSession;
import cn.edu.ruc.info.entity.PartyStage;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.entity.UserPartyProgress;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.mapper.PartyStageMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.mapper.UserPartyProgressMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminPartyService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final UserPartyProgressMapper userPartyProgressMapper;
    private final PartyStageMapper partyStageMapper;
    private final ImportSessionMapper importSessionMapper;
    private final AuditLogService auditLogService;
    private final PartyService partyService;

    public AdminPartyService(UserMapper userMapper,
            UserPartyProgressMapper userPartyProgressMapper,
            PartyStageMapper partyStageMapper,
            ImportSessionMapper importSessionMapper,
            AuditLogService auditLogService,
            PartyService partyService) {
        this.userMapper = userMapper;
        this.userPartyProgressMapper = userPartyProgressMapper;
        this.partyStageMapper = partyStageMapper;
        this.importSessionMapper = importSessionMapper;
        this.auditLogService = auditLogService;
        this.partyService = partyService;
    }

    public List<PartyStageVO> listStages() {
        return listOrderedStages().stream()
                .map(stage -> PartyStageVO.builder()
                        .id(stage.getId())
                        .stageOrder(stage.getStageOrder())
                        .title(stage.getTitle())
                        .description(stage.getDescription())
                        .defaultTime(stage.getDefaultTime())
                        .status(stage.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    public List<StudentPartyProgressSummaryVO> listStudentProgress(StudentProgressQuery query) {
        List<User> students = listStudents(query);
        if (students.isEmpty()) {
            return List.of();
        }

        List<PartyStage> stages = listOrderedStages();
        Map<Long, Map<Integer, UserPartyProgress>> progressByUserId = groupProgressByUser(students);
        List<StudentPartyProgressSummaryVO> result = new ArrayList<>();
        for (User student : students) {
            PartyService.PartyProgressVO progressVO = partyService.buildProgress(
                    stages,
                    progressByUserId.getOrDefault(student.getId(), Map.of()));
            int completedStageCount = (int) progressVO.getNodes().stream()
                    .filter(node -> "done".equals(node.getStatus()))
                    .count();
            result.add(StudentPartyProgressSummaryVO.builder()
                    .userId(student.getId())
                    .studentNo(student.getStudentNo())
                    .realName(student.getRealName())
                    .grade(student.getGrade())
                    .major(student.getMajor())
                    .identity(student.getIdentity())
                    .currentStage(progressVO.getCurrentStage())
                    .progressPercent(progressVO.getProgressPercent())
                    .completedStageCount(completedStageCount)
                    .totalStageCount(stages.size())
                    .build());
        }
        return result;
    }

    public StudentPartyProgressDetailVO getStudentProgressDetail(Long userId) {
        User student = requireStudent(userId);
        List<PartyStage> stages = listOrderedStages();
        Map<Integer, UserPartyProgress> latestProgressMap = loadLatestProgressMap(userId);
        PartyService.PartyProgressVO progressVO = partyService.buildProgress(stages, latestProgressMap);

        List<StageProgressItemVO> stageItems = new ArrayList<>();
        for (PartyStage stage : stages) {
            UserPartyProgress progress = latestProgressMap.get(stage.getId());
            stageItems.add(StageProgressItemVO.builder()
                    .stageId(stage.getId())
                    .stageOrder(stage.getStageOrder())
                    .stageTitle(stage.getTitle())
                    .stageDescription(stage.getDescription())
                    .defaultTime(stage.getDefaultTime())
                    .completed(progress != null && Boolean.TRUE.equals(progress.getCompleted()))
                    .completedAt(progress == null ? null : progress.getCompletedAt())
                    .notes(progress == null ? "" : defaultString(progress.getNotes()))
                    .status(resolveNodeStatus(progressVO, stage.getId()))
                    .build());
        }

        return StudentPartyProgressDetailVO.builder()
                .userId(student.getId())
                .studentNo(student.getStudentNo())
                .realName(student.getRealName())
                .grade(student.getGrade())
                .major(student.getMajor())
                .identity(student.getIdentity())
                .currentStage(progressVO.getCurrentStage())
                .progressPercent(progressVO.getProgressPercent())
                .nodes(progressVO.getNodes())
                .stages(stageItems)
                .todos(progressVO.getTodos())
                .build();
    }

    @Transactional
    public StudentPartyProgressDetailVO saveStudentProgress(Long userId, SaveStudentProgressRequest request, Long operatorId) {
        String auditTarget = String.valueOf(userId);
        try {
            if (request == null || request.getStages() == null || request.getStages().isEmpty()) {
                throw new RuntimeException("至少需要提交一个阶段");
            }

            User student = requireStudent(userId);
            Map<Integer, PartyStage> stageMap = listOrderedStages().stream()
                    .collect(Collectors.toMap(PartyStage::getId, stage -> stage, (left, right) -> left, LinkedHashMap::new));

            for (SaveStageProgressItem item : request.getStages()) {
                if (item == null || item.getStageId() == null) {
                    throw new RuntimeException("阶段 ID 不能为空");
                }
                PartyStage stage = stageMap.get(item.getStageId());
                if (stage == null) {
                    throw new RuntimeException("阶段不存在: " + item.getStageId());
                }
                upsertProgress(student.getId(), item.getStageId(), item.getCompleted(), item.getCompletedAt(), item.getNotes());
            }

            auditLogService.success("SAVE_PARTY_PROGRESS", student.getStudentNo() == null ? auditTarget : student.getStudentNo());
            return getStudentProgressDetail(userId);
        } catch (RuntimeException e) {
            auditLogService.failure("SAVE_PARTY_PROGRESS", auditTarget, e.getMessage());
            throw e;
        }
    }

    @Transactional
    public ImportPartyProgressResult importPartyProgress(String fileName, List<ImportPartyProgressRow> rows, Long operatorId) {
        String auditTarget = defaultIfBlank(fileName, "party_progress");
        try {
            if (rows == null) {
                throw new RuntimeException("导入数据不能为空");
            }
            int total = rows.size();
            int success = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();

            Map<String, PartyStage> stageMap = listOrderedStages().stream()
                    .collect(Collectors.toMap(stage -> stage.getTitle().trim(), stage -> stage, (left, right) -> left, LinkedHashMap::new));

            for (int i = 0; i < rows.size(); i++) {
                ImportPartyProgressRow row = rows.get(i);
                try {
                    if (row == null || isBlank(row.getStudentNo()) || isBlank(row.getStageTitle())) {
                        throw new RuntimeException("学号和阶段名称不能为空");
                    }
                    User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getStudentNo, row.getStudentNo().trim()));
                    if (user == null) {
                        throw new RuntimeException("未能找到该学号对应的用户: " + row.getStudentNo().trim());
                    }
                    PartyStage stage = stageMap.get(row.getStageTitle().trim());
                    if (stage == null) {
                        throw new RuntimeException("无效的党团阶段名称: " + row.getStageTitle());
                    }
                    upsertProgress(user.getId(), stage.getId(), row.getCompleted(), null, row.getNotes());
                    success++;
                } catch (RuntimeException ex) {
                    failed++;
                    if (errors.size() < 30) {
                        errors.add("第 " + (i + 1) + " 行：" + ex.getMessage());
                    }
                }
            }

            ImportSession session = new ImportSession();
            session.setId("partyimp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            session.setFileName(defaultIfBlank(fileName, "party_progress.xlsx"));
            session.setTotalRows(total);
            session.setSuccessRows(success);
            session.setFailedRows(failed);
            session.setImportedAt(LocalDateTime.now().format(FORMATTER));
            session.setOperatorId(operatorId);
            importSessionMapper.insert(session);

            if (success > 0 || total == 0) {
                auditLogService.success("IMPORT_PARTY_PROGRESS", session.getId());
            } else {
                auditLogService.failure("IMPORT_PARTY_PROGRESS", session.getId(), "全部 " + total + " 行导入失败");
            }

            return ImportPartyProgressResult.builder()
                    .importSession(ImportSessionVO.builder()
                            .id(session.getId())
                            .fileName(session.getFileName())
                            .totalRows(session.getTotalRows())
                            .successRows(session.getSuccessRows())
                            .failedRows(session.getFailedRows())
                            .importedAt(session.getImportedAt())
                            .build())
                    .errors(errors)
                    .message("已导入 " + success + " 行，失败 " + failed + " 行")
                    .build();
        } catch (RuntimeException e) {
            auditLogService.failure("IMPORT_PARTY_PROGRESS", auditTarget, e.getMessage());
            throw e;
        }
    }

    private List<User> listStudents(StudentProgressQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getRoleId, List.of(3, 4));
        if (query != null) {
            if (!isBlank(query.getGrade()) && !"全部".equals(query.getGrade())) {
                wrapper.eq(User::getGrade, query.getGrade().trim());
            }
            if (!isBlank(query.getMajor()) && !"全部".equals(query.getMajor())) {
                wrapper.eq(User::getMajor, query.getMajor().trim());
            }
            if (!isBlank(query.getKeyword())) {
                String keyword = "%" + query.getKeyword().trim() + "%";
                wrapper.and(w -> w.like(User::getStudentNo, keyword)
                        .or()
                        .like(User::getRealName, keyword)
                        .or()
                        .like(User::getUsername, keyword));
            }
        }
        wrapper.orderByAsc(User::getStudentNo).orderByAsc(User::getId);
        return userMapper.selectList(wrapper);
    }

    private User requireStudent(Long userId) {
        if (userId == null) {
            throw new RuntimeException("缺少 userId");
        }
        User student = userMapper.selectById(userId);
        if (student == null) {
            throw new RuntimeException("用户不存在");
        }
        if (student.getRoleId() == null || (student.getRoleId() != 3 && student.getRoleId() != 4)) {
            throw new RuntimeException("该用户不是学生");
        }
        return student;
    }

    private List<PartyStage> listOrderedStages() {
        List<PartyStage> stages = new ArrayList<>(partyStageMapper.selectList(null));
        stages.sort(Comparator.comparingInt(PartyStage::getStageOrder));
        return stages;
    }

    private Map<Long, Map<Integer, UserPartyProgress>> groupProgressByUser(List<User> users) {
        List<Long> userIds = users.stream().map(User::getId).collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<UserPartyProgress> allProgress = userPartyProgressMapper.selectList(
                new LambdaQueryWrapper<UserPartyProgress>().in(UserPartyProgress::getUserId, userIds));
        Map<Long, List<UserPartyProgress>> grouped = allProgress.stream()
                .filter(item -> item != null && item.getUserId() != null)
                .collect(Collectors.groupingBy(UserPartyProgress::getUserId));

        Map<Long, Map<Integer, UserPartyProgress>> result = new HashMap<>();
        for (Map.Entry<Long, List<UserPartyProgress>> entry : grouped.entrySet()) {
            result.put(entry.getKey(), partyService.toLatestProgressMap(entry.getValue()));
        }
        return result;
    }

    private Map<Integer, UserPartyProgress> loadLatestProgressMap(Long userId) {
        List<UserPartyProgress> progressRows = userPartyProgressMapper.selectList(
                new LambdaQueryWrapper<UserPartyProgress>().eq(UserPartyProgress::getUserId, userId));
        return partyService.toLatestProgressMap(progressRows);
    }

    private void upsertProgress(Long userId, Integer stageId, Boolean completed, LocalDateTime completedAt, String notes) {
        boolean completedValue = completed == null || completed;
        Map<Integer, UserPartyProgress> latestProgressMap = loadLatestProgressMap(userId);
        UserPartyProgress progress = latestProgressMap.get(stageId);
        if (progress == null) {
            progress = new UserPartyProgress();
            progress.setUserId(userId);
            progress.setStageId(stageId);
            progress.setCompleted(completedValue);
            progress.setCompletedAt(completedValue ? (completedAt == null ? LocalDateTime.now() : completedAt) : null);
            progress.setNotes(defaultString(notes));
            userPartyProgressMapper.insert(progress);
            return;
        }
        progress.setCompleted(completedValue);
        progress.setCompletedAt(completedValue ? (completedAt == null ? defaultCompletedAt(progress) : completedAt) : null);
        if (notes != null) {
            progress.setNotes(notes.trim());
        }
        userPartyProgressMapper.updateById(progress);
    }

    private LocalDateTime defaultCompletedAt(UserPartyProgress progress) {
        return progress.getCompletedAt() == null ? LocalDateTime.now() : progress.getCompletedAt();
    }

    private String resolveNodeStatus(PartyService.PartyProgressVO progressVO, Integer stageId) {
        if (progressVO == null || progressVO.getNodes() == null || stageId == null) {
            return "todo";
        }
        String key = "node-" + stageId;
        return progressVO.getNodes().stream()
                .filter(node -> key.equals(node.getKey()))
                .map(PartyService.Node::getStatus)
                .findFirst()
                .orElse("todo");
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Data
    public static class StudentProgressQuery {
        private String grade;
        private String major;
        private String keyword;
    }

    @Builder
    @Data
    public static class PartyStageVO {
        private Integer id;
        private Integer stageOrder;
        private String title;
        private String description;
        private String defaultTime;
        private String status;
    }

    @Builder
    @Data
    public static class StudentPartyProgressSummaryVO {
        private Long userId;
        private String studentNo;
        private String realName;
        private String grade;
        private String major;
        private String identity;
        private String currentStage;
        private Integer progressPercent;
        private Integer completedStageCount;
        private Integer totalStageCount;
    }

    @Builder
    @Data
    public static class StudentPartyProgressDetailVO {
        private Long userId;
        private String studentNo;
        private String realName;
        private String grade;
        private String major;
        private String identity;
        private String currentStage;
        private Integer progressPercent;
        private List<PartyService.Node> nodes;
        private List<StageProgressItemVO> stages;
        private List<PartyService.Todo> todos;
    }

    @Builder
    @Data
    public static class StageProgressItemVO {
        private Integer stageId;
        private Integer stageOrder;
        private String stageTitle;
        private String stageDescription;
        private String defaultTime;
        private Boolean completed;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime completedAt;
        private String notes;
        private String status;
    }

    @Data
    public static class SaveStudentProgressRequest {
        private List<SaveStageProgressItem> stages;
    }

    @Data
    public static class SaveStageProgressItem {
        private Integer stageId;
        private Boolean completed;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime completedAt;
        private String notes;
    }

    @Data
    public static class ImportPartyProgressRow {
        private String studentNo;
        private String stageTitle;
        private Boolean completed;
        private String notes;
    }

    @Builder
    @Data
    public static class ImportPartyProgressResult {
        private ImportSessionVO importSession;
        private List<String> errors;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentOptionVO {
        private Long userId;
        private String studentNo;
        private String realName;
        private String grade;
        private String major;
    }
}
