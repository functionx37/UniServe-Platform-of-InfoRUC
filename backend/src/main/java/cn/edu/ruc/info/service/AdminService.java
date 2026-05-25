package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.DashboardRequest;
import cn.edu.ruc.info.dto.DashboardVO;
import cn.edu.ruc.info.dto.DeliveryLogVO;
import cn.edu.ruc.info.dto.ImportSessionVO;
import cn.edu.ruc.info.dto.NotificationVO;
import cn.edu.ruc.info.entity.DeliveryLog;
import cn.edu.ruc.info.entity.ImportSession;
import cn.edu.ruc.info.entity.Notification;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.DeliveryLogMapper;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.mapper.NotificationMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NotificationMapper notificationMapper;
    private final DeliveryLogMapper deliveryLogMapper;
    private final ImportSessionMapper importSessionMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    public AdminService(NotificationMapper notificationMapper,
            DeliveryLogMapper deliveryLogMapper,
            ImportSessionMapper importSessionMapper,
            UserMapper userMapper,
            AuditLogService auditLogService) {
        this.notificationMapper = notificationMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.importSessionMapper = importSessionMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
    }

    public DashboardVO getDashboard(DashboardRequest request) {
        Long pendingCount = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().eq(Notification::getStatus, "待发布"));

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        if (request.getGrade() != null && !request.getGrade().equals("全部")) {
            userWrapper.eq(User::getGrade, request.getGrade());
        }
        if (request.getMajor() != null && !request.getMajor().equals("全部")) {
            userWrapper.eq(User::getMajor, request.getMajor());
        }
        if (request.getIdentity() != null && !request.getIdentity().equals("全部")) {
            userWrapper.eq(User::getIdentity, request.getIdentity());
        }
        Long targetCount = userMapper.selectCount(userWrapper);
        Long deliveryCount = deliveryLogMapper.selectCount(null);

        int successRate = 0;
        List<ImportSession> sessions = importSessionMapper.selectList(
                new LambdaQueryWrapper<ImportSession>().orderByDesc(ImportSession::getImportedAt).last("limit 1"));
        if (!sessions.isEmpty()) {
            ImportSession latest = sessions.get(0);
            if (latest.getTotalRows() != null && latest.getTotalRows() > 0) {
                successRate = (int) Math.round((latest.getSuccessRows() * 100.0) / latest.getTotalRows());
            }
        }

        return DashboardVO.builder()
                .pendingNotificationCount(pendingCount.intValue())
                .targetStudentCount(targetCount.intValue())
                .recentDeliveryCount(deliveryCount.intValue())
                .latestImportSuccessRate(successRate)
                .build();
    }

    public List<NotificationVO> listNotifications() {
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>().orderByDesc(Notification::getPublishAt)).stream()
                .map(this::toNotificationVO)
                .collect(Collectors.toList());
    }

    public List<DeliveryLogVO> listDeliveryLogs() {
        return deliveryLogMapper.selectList(
                new LambdaQueryWrapper<DeliveryLog>().orderByDesc(DeliveryLog::getSentAt)).stream()
                .map(log -> DeliveryLogVO.builder()
                        .id(log.getId())
                        .title(log.getTitle())
                        .audience(log.getAudience())
                        .channels(log.getChannels())
                        .sentAt(log.getSentAt())
                        .count(log.getCount())
                        .status(log.getStatus())
                        .build())
                .collect(Collectors.toList());
    }

    public List<ImportSessionVO> listImportSessions() {
        return importSessionMapper.selectList(
                new LambdaQueryWrapper<ImportSession>().orderByDesc(ImportSession::getImportedAt)).stream()
                .map(session -> ImportSessionVO.builder()
                        .id(session.getId())
                        .fileName(session.getFileName())
                        .totalRows(session.getTotalRows())
                        .successRows(session.getSuccessRows())
                        .failedRows(session.getFailedRows())
                        .importedAt(session.getImportedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public ImportNotificationsResult importNotifications(String fileName, List<ImportNotificationRow> rows,
            Long operatorId) {
        try {
            int failedRows = 0;
            int successRows = 0;
            for (ImportNotificationRow row : rows) {
                if (row == null || isBlank(row.getTitle()) || isBlank(row.getCategory())) {
                    failedRows++;
                    continue;
                }
                Notification notification = new Notification();
                notification.setId("policy-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                notification.setTitle(row.getTitle().trim());
                notification.setCategory(row.getCategory().trim());
                notification.setTag(categoryToTag(row.getCategory()));
                notification.setGrade(defaultIfBlank(row.getGrade(), "全部"));
                notification.setMajor(defaultIfBlank(row.getMajor(), "全部"));
                notification.setChannel(defaultIfBlank(row.getChannel(), "站内消息"));
                notification.setPublishAt(defaultIfBlank(row.getPublishAt(), "待定"));
                notification.setStatus(defaultIfBlank(row.getStatus(), "待发布"));
                notification.setCreatedBy(operatorId);
                notificationMapper.insert(notification);
                successRows++;
            }

            ImportSession session = new ImportSession();
            session.setId("import-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            session.setFileName(fileName);
            session.setTotalRows(rows.size());
            session.setSuccessRows(successRows);
            session.setFailedRows(failedRows);
            session.setImportedAt(LocalDateTime.now().format(FORMATTER));
            session.setOperatorId(operatorId);
            importSessionMapper.insert(session);

            auditLogService.success("IMPORT_NOTIFICATIONS", session.getId());
            return ImportNotificationsResult.builder()
                    .importSession(ImportSessionVO.builder()
                            .id(session.getId())
                            .fileName(session.getFileName())
                            .totalRows(session.getTotalRows())
                            .successRows(session.getSuccessRows())
                            .failedRows(session.getFailedRows())
                            .importedAt(session.getImportedAt())
                            .build())
                    .notifications(listNotifications())
                    .message("已导入 " + successRows + " 行，失败 " + failedRows + " 行")
                    .build();
        } catch (RuntimeException e) {
            auditLogService.failure("IMPORT_NOTIFICATIONS", fileName, e.getMessage());
            throw e;
        }
    }

    public PushPreviewVO previewPush(PushFilter filter) {
        List<User> users = listRecipients(filter);
        List<RecipientVO> recipients = users.stream()
                .map(user -> RecipientVO.builder()
                        .id(user.getId())
                        .studentNo(user.getStudentNo())
                        .realName(user.getRealName())
                        .grade(user.getGrade())
                        .major(user.getMajor())
                        .identity(user.getIdentity())
                        .build())
                .collect(Collectors.toList());
        return PushPreviewVO.builder()
                .recipients(recipients)
                .total(recipients.size())
                .build();
    }

    public DeliveryLogVO sendPush(SendPushRequest request, Long operatorId) {
        String auditTarget = defaultIfBlank(request.getGrade(), "全部") + "/" + defaultIfBlank(request.getMajor(), "全部")
                + "/"
                + defaultIfBlank(request.getIdentity(), "全部");
        try {
            if (isBlank(request.getTitle()) || isBlank(request.getContent())) {
                throw new RuntimeException("标题和内容不能为空");
            }
            List<User> recipients = listRecipients(PushFilter.builder()
                    .grade(request.getGrade())
                    .major(request.getMajor())
                    .identity(request.getIdentity())
                    .build());

            String now = LocalDateTime.now().format(FORMATTER);
            String channels = request.getChannels() == null ? "" : String.join("、", request.getChannels());
            DeliveryLog log = new DeliveryLog();
            log.setId("delivery-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            log.setTitle(request.getTitle().trim());
            log.setAudience(
                    defaultIfBlank(request.getGrade(), "全部") + " / " + defaultIfBlank(request.getMajor(), "全部") + " / "
                            + defaultIfBlank(request.getIdentity(), "全部"));
            log.setChannels(isBlank(channels) ? "站内消息" : channels);
            log.setSentAt(now);
            log.setCount(recipients.size());
            log.setStatus(recipients.isEmpty() ? "无匹配对象" : "已发送");
            log.setOperatorId(operatorId);
            deliveryLogMapper.insert(log);

            Notification notification = new Notification();
            notification.setId("push-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            notification.setTitle(request.getTitle().trim());
            notification.setCategory("推送");
            notification.setTag("通知");
            notification.setGrade(defaultIfBlank(request.getGrade(), "全部"));
            notification.setMajor(defaultIfBlank(request.getMajor(), "全部"));
            notification.setChannel(isBlank(channels) ? "站内消息" : channels);
            notification.setPublishAt(now);
            notification.setStatus("已发布");
            notification.setContent(request.getContent());
            notification.setCreatedBy(operatorId);
            notificationMapper.insert(notification);

            auditLogService.success("SEND_PUSH", log.getId());
            return DeliveryLogVO.builder()
                    .id(log.getId())
                    .title(log.getTitle())
                    .audience(log.getAudience())
                    .channels(log.getChannels())
                    .sentAt(log.getSentAt())
                    .count(log.getCount())
                    .status(log.getStatus())
                    .build();
        } catch (RuntimeException e) {
            auditLogService.failure("SEND_PUSH", auditTarget, e.getMessage());
            throw e;
        }
    }

    private List<User> listRecipients(PushFilter filter) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(User::getRoleId, 1).ne(User::getRoleId, 2).ne(User::getRoleId, 3);
        if (filter != null) {
            if (!isBlank(filter.getGrade()) && !"全部".equals(filter.getGrade())) {
                wrapper.eq(User::getGrade, filter.getGrade());
            }
            if (!isBlank(filter.getMajor()) && !"全部".equals(filter.getMajor())) {
                wrapper.eq(User::getMajor, filter.getMajor());
            }
            if (!isBlank(filter.getIdentity()) && !"全部".equals(filter.getIdentity())) {
                wrapper.eq(User::getIdentity, filter.getIdentity());
            }
        }
        return userMapper.selectList(wrapper);
    }

    private NotificationVO toNotificationVO(Notification notification) {
        return NotificationVO.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .category(notification.getCategory())
                .grade(notification.getGrade())
                .major(notification.getMajor())
                .channel(notification.getChannel())
                .publishAt(notification.getPublishAt())
                .status(notification.getStatus())
                .build();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String categoryToTag(String category) {
        String normalized = defaultIfBlank(category, "通知");
        return switch (normalized) {
            case "党建" -> "党团";
            case "就业" -> "就业";
            case "实习" -> "实习";
            case "竞赛" -> "竞赛";
            case "奖助", "通知" -> "学业";
            default -> normalized;
        };
    }

    @lombok.Data
    public static class ImportNotificationRow {
        private String title;
        private String category;
        private String grade;
        private String major;
        private String channel;
        private String publishAt;
        private String status;
    }

    @lombok.Builder
    @lombok.Data
    public static class ImportNotificationsResult {
        private ImportSessionVO importSession;
        private List<NotificationVO> notifications;
        private String message;
    }

    @lombok.Data
    @lombok.Builder
    public static class PushFilter {
        private String grade;
        private String major;
        private String identity;
    }

    @lombok.Data
    @lombok.Builder
    public static class RecipientVO {
        private Long id;
        private String studentNo;
        private String realName;
        private String grade;
        private String major;
        private String identity;
    }

    @lombok.Data
    @lombok.Builder
    public static class PushPreviewVO {
        private List<RecipientVO> recipients;
        private Integer total;
    }

    @lombok.Data
    public static class SendPushRequest {
        private String title;
        private String content;
        private String grade;
        private String major;
        private String identity;
        private List<String> channels;
    }
}
