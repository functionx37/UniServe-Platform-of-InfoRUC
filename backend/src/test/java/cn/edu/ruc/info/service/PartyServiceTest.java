package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.PartyStage;
import cn.edu.ruc.info.entity.UserPartyProgress;
import cn.edu.ruc.info.mapper.PartyStageMapper;
import cn.edu.ruc.info.mapper.UserPartyProgressMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyServiceTest {

    @Mock
    private PartyStageMapper partyStageMapper;

    @Mock
    private UserPartyProgressMapper userPartyProgressMapper;

    @InjectMocks
    private PartyService partyService;

    @Test
    void shouldUseCompletedTimeAndFindCurrentStage() {
        PartyStage stage1 = stage(1, 1, "提交入党申请书", "填写材料", "2026-03-12");
        PartyStage stage2 = stage(2, 2, "确定入党积极分子", "组织考察", "");
        PartyStage stage3 = stage(3, 3, "参加党课培训", "完成党课", "");

        UserPartyProgress progress1 = progress(11L, 1001L, 1, true,
                LocalDateTime.of(2026, 6, 1, 10, 30, 0), "已提交");

        when(partyStageMapper.selectList(null)).thenReturn(List.of(stage3, stage1, stage2));
        when(userPartyProgressMapper.selectList(any())).thenReturn(List.of(progress1));

        PartyService.PartyProgressVO result = partyService.getProgressForUser(1001L);

        assertEquals("确定入党积极分子", result.getCurrentStage());
        assertEquals(33, result.getProgressPercent());
        assertEquals(3, result.getNodes().size());
        assertEquals("done", result.getNodes().get(0).getStatus());
        assertEquals("2026-06-01 10:30:00", result.getNodes().get(0).getTime());
        assertEquals("current", result.getNodes().get(1).getStatus());
        assertEquals("todo", result.getNodes().get(2).getStatus());
        assertEquals(1, result.getTodos().size());
        assertEquals("完成阶段：确定入党积极分子", result.getTodos().get(0).getTitle());
    }

    @Test
    void shouldReturnFinishedWhenAllStagesCompleted() {
        PartyStage stage1 = stage(1, 1, "提交入党申请书", "填写材料", "");
        PartyStage stage2 = stage(2, 2, "确定入党积极分子", "组织考察", "");

        UserPartyProgress progress1 = progress(21L, 1002L, 1, true,
                LocalDateTime.of(2026, 6, 1, 8, 0, 0), null);
        UserPartyProgress progress2 = progress(22L, 1002L, 2, true,
                LocalDateTime.of(2026, 6, 5, 9, 0, 0), null);

        when(partyStageMapper.selectList(null)).thenReturn(List.of(stage1, stage2));
        when(userPartyProgressMapper.selectList(any())).thenReturn(List.of(progress1, progress2));

        PartyService.PartyProgressVO result = partyService.getProgressForUser(1002L);

        assertEquals("已完成全部阶段", result.getCurrentStage());
        assertEquals(100, result.getProgressPercent());
        assertTrue(result.getTodos().isEmpty());
        assertEquals("done", result.getNodes().get(0).getStatus());
        assertEquals("done", result.getNodes().get(1).getStatus());
    }

    private PartyStage stage(int id, int order, String title, String description, String defaultTime) {
        PartyStage stage = new PartyStage();
        stage.setId(id);
        stage.setStageOrder(order);
        stage.setTitle(title);
        stage.setDescription(description);
        stage.setDefaultTime(defaultTime);
        stage.setStatus("active");
        return stage;
    }

    private UserPartyProgress progress(Long id, Long userId, Integer stageId, boolean completed,
            LocalDateTime completedAt, String notes) {
        UserPartyProgress progress = new UserPartyProgress();
        progress.setId(id);
        progress.setUserId(userId);
        progress.setStageId(stageId);
        progress.setCompleted(completed);
        progress.setCompletedAt(completedAt);
        progress.setNotes(notes);
        return progress;
    }
}
