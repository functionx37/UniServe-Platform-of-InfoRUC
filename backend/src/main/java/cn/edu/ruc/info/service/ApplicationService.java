package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.ApplicationRequest;
import cn.edu.ruc.info.dto.ApplicationVO;
import cn.edu.ruc.info.entity.Application;
import cn.edu.ruc.info.mapper.ApplicationMapper;
import cn.edu.ruc.info.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 申请列表，可按中文状态筛选
     */
    public List<ApplicationVO> listApplications(String status) {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        LambdaQueryWrapper<Application> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Application::getUserId, userId);

        // 状态映射：中文 -> 数字
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

    /**
     * 新建申请
     */
    public ApplicationVO createApplication(ApplicationRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        // 简单必填校验
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
        entity.setStatus(0); // 待审

        // 将 form 对象转为 JSON 字符串存储
        try {
            entity.setForm(objectMapper.writeValueAsString(request.getForm()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("表单数据序列化失败");
        }

        // 附件 JSON 直接传入
        entity.setAttachments(request.getAttachments());

        applicationMapper.insert(entity);

        return toVO(entity);
    }

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
}