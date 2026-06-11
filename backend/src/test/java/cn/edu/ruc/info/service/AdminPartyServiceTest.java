package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.ImportSession;
import cn.edu.ruc.info.entity.PartyStage;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.entity.UserPartyProgress;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.mapper.PartyStageMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.mapper.UserPartyProgressMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPartyServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserPartyProgressMapper userPartyProgressMapper;

    @Mock
    private PartyStageMapper partyStageMapper;

    @Mock
    private ImportSessionMapper importSessionMapper;

    @Mock
    private AuditLogService auditLogService;

    private PartyService partyService;

    private AdminPartyService adminPartyService;

    @BeforeEach
    void setUp() {
        partyService = new PartyService();
        ReflectionTestUtils.setField(partyService, "partyStageMapper", partyStageMapper);
        ReflectionTestUtils.setField(partyService, "userPartyProgressMapper", userPartyProgressMapper);
        adminPartyService = new AdminPartyService(
                userMapper,
                userPartyProgressMapper,
                partyStageMapper,
                importSessionMapper,
                auditLogService,
                partyService);
    }

    @Test
    void shouldSaveStudentProgressAndReturnDetail() {
        User student = new User();
        student.setId(1001L);
        student.setRoleId(4);
        student.setStudentNo("20260001");
        student.setRealName("张三");
        student.setGrade("2026");
        student.setMajor("信息学院");
        student.setIdentity("普通学生");

        PartyStage stage1 = stage(1, 1, "提交入党申请书", "填写材料", "2026-03-12");
        PartyStage stage2 = stage(2, 2, "确定入党积极分子", "组织考察", "");
        LocalDateTime completedAt = LocalDateTime.of(2026, 6, 10, 15, 30, 0);

        when(userMapper.selectById(1001L)).thenReturn(student);
        when(partyStageMapper.selectList(null)).thenReturn(List.of(stage1, stage2));
        when(userPartyProgressMapper.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(savedProgress(501L, 1001L, 1, true, completedAt, "材料已核验")));

        AdminPartyService.SaveStageProgressItem item = new AdminPartyService.SaveStageProgressItem();
        item.setStageId(1);
        item.setCompleted(true);
        item.setCompletedAt(completedAt);
        item.setNotes("材料已核验");
        AdminPartyService.SaveStudentProgressRequest request = new AdminPartyService.SaveStudentProgressRequest();
        request.setStages(List.of(item));

        AdminPartyService.StudentPartyProgressDetailVO result =
                adminPartyService.saveStudentProgress(1001L, request, 9001L);

        ArgumentCaptor<UserPartyProgress> progressCaptor = ArgumentCaptor.forClass(UserPartyProgress.class);
        verify(userPartyProgressMapper).insert(progressCaptor.capture());
        UserPartyProgress inserted = progressCaptor.getValue();
        assertEquals(1001L, inserted.getUserId());
        assertEquals(1, inserted.getStageId());
        assertTrue(Boolean.TRUE.equals(inserted.getCompleted()));
        assertEquals(completedAt, inserted.getCompletedAt());
        assertEquals("材料已核验", inserted.getNotes());

        assertEquals(1001L, result.getUserId());
        assertEquals("20260001", result.getStudentNo());
        assertEquals(50, result.getProgressPercent());
        assertEquals("确定入党积极分子", result.getCurrentStage());
        assertEquals(2, result.getStages().size());
        assertTrue(result.getStages().get(0).getCompleted());
        assertEquals(completedAt, result.getStages().get(0).getCompletedAt());
        assertEquals("材料已核验", result.getStages().get(0).getNotes());
        assertEquals("done", result.getStages().get(0).getStatus());
        assertEquals("current", result.getStages().get(1).getStatus());
        verify(auditLogService).success("SAVE_PARTY_PROGRESS", "20260001");
    }

    @Test
    void shouldImportPartyProgressRowsAndRecordSession() {
        User student = new User();
        student.setId(1001L);
        student.setRoleId(4);
        student.setStudentNo("20260001");
        student.setRealName("张三");

        PartyStage stage = stage(1, 1, "提交入党申请书", "填写材料", "");

        when(userMapper.selectOne(any())).thenReturn(student);
        when(partyStageMapper.selectList(null)).thenReturn(List.of(stage));
        when(userPartyProgressMapper.selectList(any()))
                .thenReturn(List.of());

        AdminPartyService.ImportPartyProgressRow row = new AdminPartyService.ImportPartyProgressRow();
        row.setStudentNo("20260001");
        row.setStageTitle("提交入党申请书");
        row.setCompleted(true);
        row.setNotes("导入备注");

        AdminPartyService.ImportPartyProgressResult result =
                adminPartyService.importPartyProgress("party_progress.xlsx", List.of(row), 9001L);

        ArgumentCaptor<UserPartyProgress> progressCaptor = ArgumentCaptor.forClass(UserPartyProgress.class);
        verify(userPartyProgressMapper).insert(progressCaptor.capture());
        UserPartyProgress inserted = progressCaptor.getValue();
        assertEquals(1001L, inserted.getUserId());
        assertEquals(1, inserted.getStageId());
        assertTrue(inserted.getCompleted());
        assertEquals("导入备注", inserted.getNotes());
        assertNotNull(inserted.getCompletedAt());

        ArgumentCaptor<ImportSession> sessionCaptor = ArgumentCaptor.forClass(ImportSession.class);
        verify(importSessionMapper).insert(sessionCaptor.capture());
        ImportSession session = sessionCaptor.getValue();
        assertEquals("party_progress.xlsx", session.getFileName());
        assertEquals(1, session.getTotalRows());
        assertEquals(1, session.getSuccessRows());
        assertEquals(0, session.getFailedRows());

        assertEquals("已导入 1 行，失败 0 行", result.getMessage());
        assertTrue(result.getErrors().isEmpty());
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

    private UserPartyProgress savedProgress(Long id, Long userId, Integer stageId, boolean completed,
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
