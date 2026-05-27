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

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PartyService {

    @Autowired
    private PartyStageMapper partyStageMapper;
    @Autowired
    private UserPartyProgressMapper userPartyProgressMapper;

    public PartyProgressVO getProgress() {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        // 获取标准流程阶段（按 order 排序）
        List<PartyStage> stages = partyStageMapper.selectList(null);
        stages.sort(Comparator.comparingInt(PartyStage::getStageOrder));

        // 获取用户进度
        List<UserPartyProgress> userProgress = userPartyProgressMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserPartyProgress>()
                        .eq(UserPartyProgress::getUserId, userId));
        Set<Integer> completedStageIds = userProgress.stream()
                .filter(UserPartyProgress::getCompleted)
                .map(UserPartyProgress::getStageId)
                .collect(Collectors.toSet());

        // 构造节点
        List<Node> nodes = new ArrayList<>();
        int currentIndex = -1;
        for (int i = 0; i < stages.size(); i++) {
            PartyStage stage = stages.get(i);
            boolean done = completedStageIds.contains(stage.getId());
            // 第一个未完成的阶段即为当前阶段
            if (!done && currentIndex == -1) {
                currentIndex = i;
            }
            String status = done ? "done" : (i == currentIndex ? "current" : "todo");
            nodes.add(Node.builder()
                    .key("node-" + stage.getId())
                    .title(stage.getTitle())
                    .desc(stage.getDescription())
                    .time(stage.getDefaultTime())
                    .status(status)
                    .build());
        }

        // 计算进度百分比
        int totalStages = stages.size();
        int doneCount = (int) nodes.stream().filter(n -> "done".equals(n.getStatus())).count();
        int percent = totalStages > 0 ? (doneCount * 100 / totalStages) : 0;

        // 待办：当前阶段对应的提示（简单用阶段描述）
        List<Todo> todos = new ArrayList<>();
        if (currentIndex >= 0 && currentIndex < stages.size()) {
            PartyStage currentStage = stages.get(currentIndex);
            todos.add(Todo.builder()
                    .title("完成阶段：" + currentStage.getTitle())
                    .dueAt("")
                    .note(currentStage.getDescription())
                    .build());
        }

        return PartyProgressVO.builder()
                .currentStage(currentIndex >= 0 ? stages.get(currentIndex).getTitle() : "未开始")
                .progressPercent(percent)
                .nodes(nodes)
                .todos(todos)
                .build();
    }

    // 内部 VO 定义
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