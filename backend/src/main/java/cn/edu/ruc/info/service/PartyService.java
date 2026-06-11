package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.PartyStage;
import cn.edu.ruc.info.entity.UserPartyProgress;
import cn.edu.ruc.info.mapper.PartyStageMapper;
import cn.edu.ruc.info.mapper.UserPartyProgressMapper;
import cn.edu.ruc.info.util.UserContext;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PartyService {

    private static final DateTimeFormatter COMPLETED_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private PartyStageMapper partyStageMapper;
    @Autowired
    private UserPartyProgressMapper userPartyProgressMapper;

    public PartyProgressVO getProgress() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        return getProgressForUser(userId);
    }

    public PartyProgressVO getProgressForUser(Long userId) {
        if (userId == null) {
            throw new RuntimeException("用户不存在");
        }
        List<UserPartyProgress> userProgress = userPartyProgressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPartyProgress>()
                        .eq(UserPartyProgress::getUserId, userId));
        return buildProgress(listOrderedStages(), toLatestProgressMap(userProgress));
    }

    List<PartyStage> listOrderedStages() {
        List<PartyStage> stages = new ArrayList<>(partyStageMapper.selectList(null));
        stages.sort(Comparator.comparingInt(PartyStage::getStageOrder));
        return stages;
    }

    Map<Integer, UserPartyProgress> toLatestProgressMap(List<UserPartyProgress> progressRows) {
        Map<Integer, UserPartyProgress> latestProgressMap = new LinkedHashMap<>();
        if (progressRows == null) {
            return latestProgressMap;
        }
        for (UserPartyProgress progress : progressRows) {
            if (progress == null || progress.getStageId() == null) {
                continue;
            }
            UserPartyProgress current = latestProgressMap.get(progress.getStageId());
            if (current == null || isNewer(progress, current)) {
                latestProgressMap.put(progress.getStageId(), progress);
            }
        }
        return latestProgressMap;
    }

    PartyProgressVO buildProgress(List<PartyStage> stages, Map<Integer, UserPartyProgress> latestProgressMap) {
        List<Node> nodes = new ArrayList<>();
        int currentIndex = -1;
        int doneCount = 0;
        for (int i = 0; i < stages.size(); i++) {
            PartyStage stage = stages.get(i);
            UserPartyProgress progress = latestProgressMap.get(stage.getId());
            boolean done = progress != null && Boolean.TRUE.equals(progress.getCompleted());
            if (!done && currentIndex == -1) {
                currentIndex = i;
            }
            if (done) {
                doneCount++;
            }
            String status = done ? "done" : (i == currentIndex ? "current" : "todo");
            nodes.add(Node.builder()
                    .key("node-" + stage.getId())
                    .title(stage.getTitle())
                    .desc(stage.getDescription())
                    .time(resolveNodeTime(stage, progress, done))
                    .status(status)
                    .build());
        }

        int totalStages = stages.size();
        int percent = totalStages > 0 ? (doneCount * 100 / totalStages) : 0;

        List<Todo> todos = new ArrayList<>();
        if (currentIndex >= 0 && currentIndex < stages.size()) {
            PartyStage currentStage = stages.get(currentIndex);
            todos.add(Todo.builder()
                    .title("完成阶段：" + currentStage.getTitle())
                    .dueAt("")
                    .note(currentStage.getDescription())
                    .build());
        }

        String currentStageName;
        if (stages.isEmpty()) {
            currentStageName = "未配置阶段";
        } else if (currentIndex >= 0) {
            currentStageName = stages.get(currentIndex).getTitle();
        } else {
            currentStageName = "已完成全部阶段";
        }

        return PartyProgressVO.builder()
                .currentStage(currentStageName)
                .progressPercent(percent)
                .nodes(nodes)
                .todos(todos)
                .build();
    }

    private boolean isNewer(UserPartyProgress candidate, UserPartyProgress baseline) {
        Long candidateId = candidate.getId();
        Long baselineId = baseline.getId();
        if (candidateId != null && baselineId != null) {
            return candidateId > baselineId;
        }
        LocalDateTime candidateAt = candidate.getCompletedAt();
        LocalDateTime baselineAt = baseline.getCompletedAt();
        if (candidateAt != null && baselineAt != null) {
            return candidateAt.isAfter(baselineAt);
        }
        return candidateId != null;
    }

    private String resolveNodeTime(PartyStage stage, UserPartyProgress progress, boolean done) {
        if (done && progress != null && progress.getCompletedAt() != null) {
            return COMPLETED_AT_FORMATTER.format(progress.getCompletedAt());
        }
        return blankToEmpty(stage.getDefaultTime());
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    @Data
    @Builder
    public static class PartyProgressVO {
        private String currentStage;
        private int progressPercent;
        private List<Node> nodes;
        private List<Todo> todos;
    }

    @Data
    @Builder
    public static class Node {
        private String key;
        private String title;
        private String desc;
        private String time;
        private String status; // done, current, todo
    }

    @Data
    @Builder
    public static class Todo {
        private String title;
        private String dueAt;
        private String note;
    }
}
