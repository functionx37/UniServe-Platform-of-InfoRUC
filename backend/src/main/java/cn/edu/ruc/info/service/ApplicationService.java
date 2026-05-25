package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.ApplicationRequest;
import cn.edu.ruc.info.dto.ApplicationVO;
import cn.edu.ruc.info.entity.Application;
import cn.edu.ruc.info.entity.ApprovalRecord;
import cn.edu.ruc.info.entity.GeneratedProof;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.ApplicationMapper;
import cn.edu.ruc.info.mapper.ApprovalRecordMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.util.JsonUtils;
import cn.edu.ruc.info.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ApplicationMapper applicationMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final UserMapper userMapper;
    private final JsonUtils jsonUtils;
    private final ProofGenerationService proofGenerationService;

    public ApplicationService(ApplicationMapper applicationMapper,
            ApprovalRecordMapper approvalRecordMapper,
            UserMapper userMapper,
            JsonUtils jsonUtils,
            ProofGenerationService proofGenerationService) {
        this.applicationMapper = applicationMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.userMapper = userMapper;
        this.jsonUtils = jsonUtils;
        this.proofGenerationService = proofGenerationService;
    }

    public List<ApplicationVO> listApplications(String status) {
        Long userId = requireUserId();
        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Application::getUserId, userId);
        if (status != null && !status.equals("全部")) {
            Integer code = statusToCode(status);
            if (code != null) {
                wrapper.eq(Application::getStatus, code);
            }
        }
        wrapper.orderByDesc(Application::getCreatedAt);
        return applicationMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public ApplicationVO createApplication(ApplicationRequest request) {
        Long userId = requireUserId();
        if (!StringUtils.hasText(request.getTypeKey())) {
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
        entity.setForm(jsonUtils.toJson(request.getForm()));
        entity.setAttachments(request.getAttachments() == null ? "[]" : jsonUtils.toJson(request.getAttachments()));
        applicationMapper.insert(entity);
        return toVO(entity);
    }

    public ApplicationDetailVO getApplicationDetail(Long applicationId) {
        Application entity = requireVisibleApplication(applicationId);
        List<ApprovalRecord> records = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getApplicationId, applicationId)
                        .orderByAsc(ApprovalRecord::getCreatedAt));

        GeneratedProof proof = proofGenerationService.findByApplicationId(applicationId);
        Map<String, Object> form = jsonUtils.toMap(entity.getForm());
        List<Map<String, Object>> attachments = jsonUtils.toListOfMap(entity.getAttachments());
        List<ApprovalTimelineNode> approvals = buildApprovals(entity, records, proof);
        List<ApprovalOpinionVO> approvalOpinions = records.stream()
                .filter(record -> StringUtils.hasText(record.getOpinion()))
                .map(record -> ApprovalOpinionVO.builder()
                        .step(record.getStepTitle())
                        .time(record.getCreatedAt() == null ? null : record.getCreatedAt().format(FORMATTER))
                        .opinion(record.getOpinion())
                        .build())
                .collect(Collectors.toList());

        return ApplicationDetailVO.builder()
                .id(entity.getId())
                .typeKey(entity.getTypeKey())
                .typeLabel(entity.getTypeLabel())
                .title(entity.getTitle())
                .status(codeToStatus(entity.getStatus()))
                .form(form)
                .attachments(attachments)
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().format(FORMATTER))
                .updatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(FORMATTER))
                .approvals(approvals)
                .approvalOpinions(approvalOpinions)
                .result(buildResult(proof))
                .build();
    }

    @Transactional
    public void auditApplication(Long applicationId, String action, String opinion) {
        Long approverId = requireUserId();
        Integer role = UserContext.getRoleId();
        if (role == null || (role != 1 && role != 2)) {
            throw new RuntimeException("无审批权限");
        }

        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new RuntimeException("申请不存在");
        }

        int newStatus;
        if ("pass".equalsIgnoreCase(action)) {
            if (application.getStatus() != 0) {
                throw new RuntimeException("当前状态不允许此操作");
            }
            newStatus = 1;
        } else if ("reject".equalsIgnoreCase(action)) {
            if (application.getStatus() != 0) {
                throw new RuntimeException("当前状态不允许此操作");
            }
            newStatus = 2;
        } else if ("withdraw".equalsIgnoreCase(action)) {
            if (application.getStatus() != 1 && application.getStatus() != 2) {
                throw new RuntimeException("只有已通过或已驳回的申请才能撤回");
            }
            newStatus = 0;
        } else {
            throw new RuntimeException("无效的操作");
        }

        application.setStatus(newStatus);
        application.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(application);

        ApprovalRecord record = new ApprovalRecord();
        record.setApplicationId(applicationId);
        record.setApproverId(approverId);
        record.setStepTitle("管理员审批");
        record.setStatus(newStatus == 1 ? 1 : newStatus == 2 ? 2 : 3);
        record.setOpinion(opinion);
        approvalRecordMapper.insert(record);

        if (newStatus == 1 && supportsProof(application.getTypeKey())) {
            User applicant = userMapper.selectById(application.getUserId());
            if (applicant == null) {
                throw new RuntimeException("申请用户不存在，无法生成证明");
            }
            proofGenerationService.generate(
                    applicationId,
                    application.getTypeKey(),
                    applicant,
                    jsonUtils.toMap(application.getForm()));
        }
    }

    public Application requireVisibleApplication(Long applicationId) {
        Application entity = applicationMapper.selectById(applicationId);
        if (entity == null) {
            throw new RuntimeException("申请不存在");
        }

        Long currentUserId = UserContext.getUserId();
        Integer currentRole = UserContext.getRoleId();
        if ((currentRole == null || (currentRole != 1 && currentRole != 2))
                && !Objects.equals(entity.getUserId(), currentUserId)) {
            throw new RuntimeException("无权限查看此申请");
        }
        return entity;
    }

    private ApplicationVO toVO(Application entity) {
        return ApplicationVO.builder()
                .id(entity.getId())
                .typeKey(entity.getTypeKey())
                .typeLabel(entity.getTypeLabel())
                .title(entity.getTitle())
                .status(codeToStatus(entity.getStatus()))
                .form(jsonUtils.toMap(entity.getForm()))
                .attachments(jsonUtils.toListOfMap(entity.getAttachments()))
                .createdAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().format(FORMATTER))
                .updatedAt(entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().format(FORMATTER))
                .build();
    }

    private List<ApprovalTimelineNode> buildApprovals(Application application,
            List<ApprovalRecord> records,
            GeneratedProof proof) {
        ApprovalRecord audit = records.isEmpty() ? null : records.get(records.size() - 1);
        List<ApprovalTimelineNode> nodes = new ArrayList<>();
        nodes.add(ApprovalTimelineNode.builder()
                .key("submit")
                .title("提交申请")
                .desc("申请已提交，等待管理员处理")
                .time(application.getCreatedAt() == null ? null : application.getCreatedAt().format(FORMATTER))
                .status("done")
                .build());

        if (application.getStatus() == 0) {
            nodes.add(ApprovalTimelineNode.builder()
                    .key("audit")
                    .title("管理员审批")
                    .desc("管理员正在审核材料")
                    .time(audit == null || audit.getCreatedAt() == null ? null : audit.getCreatedAt().format(FORMATTER))
                    .status("current")
                    .build());
            nodes.add(ApprovalTimelineNode.builder()
                    .key("result")
                    .title("结果反馈")
                    .desc("审核完成后将反馈处理结果")
                    .time(null)
                    .status("todo")
                    .build());
            return nodes;
        }

        nodes.add(ApprovalTimelineNode.builder()
                .key("audit")
                .title("管理员审批")
                .desc(application.getStatus() == 1 ? "申请已审核通过" : "申请已被驳回")
                .time(audit == null || audit.getCreatedAt() == null ? null : audit.getCreatedAt().format(FORMATTER))
                .status("done")
                .build());

        if (application.getStatus() == 1 && supportsProof(application.getTypeKey())) {
            nodes.add(ApprovalTimelineNode.builder()
                    .key("proof")
                    .title("生成证明")
                    .desc(proof == null ? "通过后待生成电子证明" : "电子证明已生成，可在详情页下载")
                    .time(proof == null || proof.getCreatedAt() == null ? null : proof.getCreatedAt().format(FORMATTER))
                    .status(proof == null ? "current" : "done")
                    .build());
        } else {
            nodes.add(ApprovalTimelineNode.builder()
                    .key("result")
                    .title("结果反馈")
                    .desc(application.getStatus() == 1 ? "申请已处理完成" : "请根据驳回意见补充材料后重新提交")
                    .time(audit == null || audit.getCreatedAt() == null ? null : audit.getCreatedAt().format(FORMATTER))
                    .status("done")
                    .build());
        }
        return nodes;
    }

    private ResultFileVO buildResult(GeneratedProof proof) {
        if (proof == null) {
            return null;
        }
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/files/proofs/")
                .path(proof.getId())
                .toUriString();
        return ResultFileVO.builder()
                .downloadable(true)
                .title(proof.getFileName())
                .url(url)
                .build();
    }

    private boolean supportsProof(String typeKey) {
        return "enrollment_cert".equals(typeKey) || "political_cert".equals(typeKey);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        return userId;
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

    @Data
    @Builder
    public static class ApplicationDetailVO {
        private Long id;
        private String typeKey;
        private String typeLabel;
        private String title;
        private String status;
        private Map<String, Object> form;
        private List<Map<String, Object>> attachments;
        private String createdAt;
        private String updatedAt;
        private List<ApprovalTimelineNode> approvals;
        private List<ApprovalOpinionVO> approvalOpinions;
        private ResultFileVO result;
    }

    @Data
    @Builder
    public static class ApprovalTimelineNode {
        private String key;
        private String title;
        private String desc;
        private String time;
        private String status;
    }

    @Data
    @Builder
    public static class ApprovalOpinionVO {
        private String step;
        private String time;
        private String opinion;
    }

    @Data
    @Builder
    public static class ResultFileVO {
        private Boolean downloadable;
        private String title;
        private String url;
    }
}
