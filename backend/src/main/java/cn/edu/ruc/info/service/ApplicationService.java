package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.ApplicationRequest;
import cn.edu.ruc.info.dto.ApplicationVO;
import cn.edu.ruc.info.entity.Application;
import cn.edu.ruc.info.entity.ApprovalRecord;
import cn.edu.ruc.info.mapper.ApplicationMapper;
import cn.edu.ruc.info.mapper.ApprovalRecordMapper;
import cn.edu.ruc.info.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    @Autowired
    private ApplicationMapper applicationMapper;

    @Autowired
    private ApprovalRecordMapper approvalRecordMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ========== 学生端：列表 ==========
    public List<ApplicationVO> listApplications(String status) {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Application::getUserId, userId);

        if (status != null && !status.equals("全部")) {
            Integer code = statusToCode(status);
            if (code != null) {
                wrapper.eq(Application::getStatus, code);
            }
        }
        wrapper.orderByDesc(Application::getCreatedAt);

        List<Application> list = applicationMapper.selectList(wrapper);
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ========== 学生端：新建 ==========
    public ApplicationVO createApplication(ApplicationRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        if (request.getTypeKey() == null || request.getTypeKey().isBlank()) {
            throw new RuntimeException("申请类型不能为空");
        }
        if (request.getForm() == null) {
            throw new RuntimeException("表单内容不能为空");
        }

        Application entity = new Application();
        entity.setUserId(userId);
        entity.setTypeKey(request.getTypeKey());
        entity.setTypeLabel(typeKeyToLabel(request.getTypeKey()));
        entity.setTitle(typeKeyToLabel(request.getTypeKey()) + "申请");
        entity.setStatus(0);

        try {
            entity.setForm(objectMapper.writeValueAsString(request.getForm()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("表单数据序列化失败");
        }

        entity.setAttachments(request.getAttachments());

        applicationMapper.insert(entity);
        return toVO(entity);
    }

    // ========== 详情查询（学生/管理员通用，自动权限判断） ==========
    public ApplicationDetailVO getApplicationDetail(Long applicationId) {
        Application entity = applicationMapper.selectById(applicationId);
        if (entity == null)
            throw new RuntimeException("申请不存在");

        Long currentUserId = UserContext.getUserId();
        Integer currentRole = UserContext.getRoleId();

        // 权限检查：非管理员只能查看自己的申请
        if ((currentRole == null || (currentRole != 1 && currentRole != 2)) &&
                !entity.getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权限查看此申请");
        }

        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApplicationId, applicationId)
                        .orderByAsc(ApprovalRecord::getCreatedAt));

        List<ApprovalVO> approvals = records.stream().map(r -> ApprovalVO.builder()
                .step(r.getStepTitle())
                .time(r.getCreatedAt() != null ? r.getCreatedAt().format(FORMATTER) : null)
                .opinion(r.getOpinion())
                .status(r.getStatus() == 1 ? "通过" : "驳回")
                .build()).collect(Collectors.toList());

        return ApplicationDetailVO.builder()
                .id(entity.getId())
                .typeKey(entity.getTypeKey())
                .typeLabel(entity.getTypeLabel())
                .title(entity.getTitle())
                .status(codeToStatus(entity.getStatus()))
                .form(entity.getForm())
                .attachments(entity.getAttachments())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(FORMATTER) : null)
                .approvals(approvals)
                .build();
    }

    // ========== 审批操作（仅管理员可调用） ==========
    public void auditApplication(Long applicationId, String action, String opinion) {
        Long approverId = UserContext.getUserId();
        Integer role = UserContext.getRoleId();
        if (approverId == null || (role != 1 && role != 2)) {
            throw new RuntimeException("无审批权限");
        }

        Application application = applicationMapper.selectById(applicationId);
        if (application == null)
            throw new RuntimeException("申请不存在");

        int newStatus;
        if ("pass".equalsIgnoreCase(action)) {
            if (application.getStatus() != 0)
                throw new RuntimeException("当前状态不允许此操作");
            newStatus = 1;
        } else if ("reject".equalsIgnoreCase(action)) {
            if (application.getStatus() != 0)
                throw new RuntimeException("当前状态不允许此操作");
            newStatus = 2;
        } else if ("withdraw".equalsIgnoreCase(action)) {
            if (application.getStatus() != 1 && application.getStatus() != 2)
                throw new RuntimeException("只有已通过或已驳回的申请才能撤回");
            newStatus = 0;
        } else {
            throw new RuntimeException("无效的操作");
        }

        application.setStatus(newStatus);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);

        // 写入审批记录
        ApprovalRecord record = new ApprovalRecord();
        record.setApplicationId(applicationId);
        record.setApproverId(approverId);
        record.setStepTitle("管理员审批");
        if (newStatus == 1) {
            record.setStatus(1); // 通过
        } else if (newStatus == 2) {
            record.setStatus(2); // 驳回
        } else {
            record.setStatus(3); // 撤回（用3表示，无明确枚举，暂时这样）
        }
        record.setOpinion(opinion);
        approvalRecordMapper.insert(record);
    }

    // ========== 辅助方法 ==========
    private ApplicationVO toVO(Application entity) {
        return ApplicationVO.builder()
                .id(entity.getId())
                .typeKey(entity.getTypeKey())
                .typeLabel(entity.getTypeLabel())
                .title(entity.getTitle())
                .status(codeToStatus(entity.getStatus()))
                .form(entity.getForm())
                .attachments(entity.getAttachments())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(FORMATTER) : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(FORMATTER) : null)
                .build();
    }

    private Integer statusToCode(String status) {
        return switch (status) {
            case "审批中" -> 0;
            case "已通过" -> 1;
            case "已驳回" -> 2;
            case "已撤回" -> 3;
            default -> null;
        };
    }

    private String codeToStatus(Integer code) {
        return switch (code) {
            case 0 -> "审批中";
            case 1 -> "已通过";
            case 2 -> "已驳回";
            case 3 -> "已撤回";
            default -> "未知";
        };
    }

    private String typeKeyToLabel(String key) {
        return switch (key) {
            case "leave" -> "请假申请";
            case "enrollment_cert" -> "在读证明";
            case "political_cert" -> "政治面貌证明";
            default -> "事务申请";
        };
    }

    // ========== 内部 VO 定义 ==========
    @Data
    @Builder
    public static class ApplicationDetailVO {
        private Long id;
        private String typeKey;
        private String typeLabel;
        private String title;
        private String status;
        private String form;
        private String attachments;
        private String createdAt;
        private String updatedAt;
        private List<ApprovalVO> approvals;
    }

    @Data
    @Builder
    public static class ApprovalVO {
        private String step;
        private String time;
        private String opinion;
        private String status;
    }
}