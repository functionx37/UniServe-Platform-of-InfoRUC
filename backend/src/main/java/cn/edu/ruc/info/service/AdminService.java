package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.DashboardRequest;
import cn.edu.ruc.info.dto.DashboardVO;
import cn.edu.ruc.info.dto.DeliveryLogVO;
import cn.edu.ruc.info.dto.ImportSessionVO;
import cn.edu.ruc.info.dto.NotificationVO;
import cn.edu.ruc.info.entity.AuditLog;
import cn.edu.ruc.info.entity.DeliveryLog;
import cn.edu.ruc.info.entity.ImportSession;
import cn.edu.ruc.info.entity.Notification;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.DeliveryLogMapper;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.mapper.NotificationMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.mapper.UserPartyProgressMapper;
import cn.edu.ruc.info.mapper.PartyStageMapper;
import cn.edu.ruc.info.entity.UserPartyProgress;
import cn.edu.ruc.info.entity.PartyStage;
import cn.edu.ruc.info.util.EncryptUtil;
import cn.edu.ruc.info.util.MaskUtil;
import cn.edu.ruc.info.util.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ===== 导入验证常量 =====
    // 合法年级：允许 "2023" 或 "2023级" 这类格式
    private static final Set<String> VALID_GRADES = Set.of(
            "2018", "2019", "2020", "2021", "2022", "2023", "2024", "2025", "2026");


    // 合法专业列表（与前端 majorOptions 保持一致）
    private static final Set<String> VALID_MAJORS = Set.of(
            "计算机科学与技术",
            "数据科学与大数据技术",
            "信息安全",
            "人工智能",
            "信息学院");

    // 合法身份列表（与前端 identityOptions 保持一致）
    private static final Set<String> VALID_IDENTITIES = Set.of(
            "普通学生",
            "班团骨干",
            "研究生",
            "预备党员");

    // 学号格式：至少8位数字（例如 20260001）
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("^\\d{8,}$");
    // ===== 结束验证常量 =====

    private final NotificationMapper notificationMapper;
    private final DeliveryLogMapper deliveryLogMapper;
    private final ImportSessionMapper importSessionMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final EncryptUtil encryptUtil;
    private final UserPartyProgressMapper userPartyProgressMapper;
    private final PartyStageMapper partyStageMapper;

    public AdminService(NotificationMapper notificationMapper,
            DeliveryLogMapper deliveryLogMapper,
            ImportSessionMapper importSessionMapper,
            UserMapper userMapper,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            EncryptUtil encryptUtil,
            UserPartyProgressMapper userPartyProgressMapper,
            PartyStageMapper partyStageMapper) {
        this.notificationMapper = notificationMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.importSessionMapper = importSessionMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.encryptUtil = encryptUtil;
        this.userPartyProgressMapper = userPartyProgressMapper;
        this.partyStageMapper = partyStageMapper;
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
        Long targetCount;
        if (request.getIdentity() != null && !request.getIdentity().equals("全部")) {
            targetCount = (long) filterUsersByIdentity(userMapper.selectList(userWrapper), request.getIdentity())
                    .size();
        } else {
            targetCount = userMapper.selectCount(userWrapper);
        }
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
                // 导入时发布时间设为当前系统时间
                notification.setPublishAt(LocalDateTime.now().format(FORMATTER));
                notification.setStatus(defaultIfBlank(row.getStatus(), "待发布"));
                notification.setContent(defaultIfBlank(row.getContent(), ""));
                notification.setLinks(defaultIfBlank(row.getLinks(), "[]"));
                notification.setCreatedBy(operatorId);
                notificationMapper.insert(notification);

                // 如果分类是“推送”或“精准推送”且状态是“已发布”，同步创建一条发送日志
                String category = notification.getCategory();
                if (("推送".equals(category) || "精准推送".equals(category)) && "已发布".equals(notification.getStatus())) {
                    DeliveryLog log = new DeliveryLog();
                    log.setId("delivery-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                    log.setTitle(notification.getTitle());
                    log.setAudience(notification.getGrade() + " / " + notification.getMajor() + " / 全部");
                    log.setChannels(notification.getChannel());
                    log.setSentAt(notification.getPublishAt());
                    log.setCount(0); // 导入的推送记录暂不统计人数，或后续异步统计
                    log.setStatus("已发送");
                    log.setOperatorId(operatorId);
                    deliveryLogMapper.insert(log);
                }

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

            if (successRows > 0 || rows.isEmpty()) {
                auditLogService.success("IMPORT_NOTIFICATIONS", session.getId());
            } else {
                auditLogService.failure("IMPORT_NOTIFICATIONS", session.getId(), "全部 " + rows.size() + " 行导入失败");
            }
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

            if (recipients.isEmpty()) {
                // 不抛异常，允许记录一条“无法推送”的日志
            }

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
            log.setStatus(recipients.isEmpty() ? "无法推送" : "已发送");
            log.setOperatorId(operatorId);
            deliveryLogMapper.insert(log);

            if (!recipients.isEmpty()) {
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
            }

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

    @Transactional
    public void updateNotificationStatus(String id, String status, Long operatorId) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }

        String oldStatus = notification.getStatus();
        notification.setStatus(status);
        notificationMapper.updateById(notification);

        // 如果是推送类的通知
        String category = notification.getCategory() == null ? "" : notification.getCategory().trim();
        if ("推送".equals(category) || "精准推送".equals(category)) {
            // 情况 A：是从“发送记录”产生的通知（id 以 push- 开头），同步更新日志状态
            if (id != null && id.startsWith("push-")) {
                LambdaQueryWrapper<DeliveryLog> logWrapper = new LambdaQueryWrapper<>();
                logWrapper.eq(DeliveryLog::getTitle, notification.getTitle());
                logWrapper.orderByDesc(DeliveryLog::getSentAt);
                logWrapper.last("limit 1");
                DeliveryLog log = deliveryLogMapper.selectOne(logWrapper);
                if (log != null) {
                    log.setStatus("已发布".equals(status) ? "已发送" : "已撤回");
                    deliveryLogMapper.updateById(log);
                }
            }
            // 情况 B：是导入的推送类通知（id 以 policy- 开头），且从“待发布”变为“已发布”
            else if (id != null && id.startsWith("policy-") && "待发布".equals(oldStatus) && "已发布".equals(status)) {
                DeliveryLog log = new DeliveryLog();
                log.setId("delivery-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                log.setTitle(notification.getTitle());
                log.setAudience(notification.getGrade() + " / " + notification.getMajor() + " / 全部");
                log.setChannels(notification.getChannel());
                log.setSentAt(notification.getPublishAt());
                log.setCount(0);
                log.setStatus("已发送");
                log.setOperatorId(operatorId);
                deliveryLogMapper.insert(log);
            }
        }

        auditLogService.success("UPDATE_NOTIFICATION_STATUS", id + ":" + status);
    }

    public List<AuditLog> listAuditLogs(String action, Integer limit) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (!isBlank(action)) {
            wrapper.eq(AuditLog::getAction, action);
        }
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        wrapper.last("limit " + (limit != null ? limit : 20));
        return auditLogService.list(wrapper);
    }

    public List<UserVO> listUsers(UserQuery query) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (query != null) {
            if (query.getRoleId() != null) {
                wrapper.eq(User::getRoleId, query.getRoleId());
            } else if (Boolean.TRUE.equals(query.getIsStudentOnly())) {
                wrapper.in(User::getRoleId, List.of(3, 4));
            }
            if (!isBlank(query.getGrade()) && !"全部".equals(query.getGrade())) {
                wrapper.eq(User::getGrade, query.getGrade());
            }
            if (!isBlank(query.getMajor()) && !"全部".equals(query.getMajor())) {
                wrapper.eq(User::getMajor, query.getMajor());
            }
            if (!isBlank(query.getKeyword())) {
                String keyword = "%" + query.getKeyword().trim() + "%";
                wrapper.and(w -> w.like(User::getUsername, keyword)
                        .or()
                        .like(User::getStudentNo, keyword)
                        .or()
                        .like(User::getRealName, keyword));
            }
        }
        wrapper.orderByDesc(User::getUpdatedAt);
        return userMapper.selectList(wrapper).stream()
                .map(this::toUserVO)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserVO createUser(CreateUserRequest request, Long operatorId, Integer operatorRole) {
        try {
            if (request == null) {
                throw new RuntimeException("请求不能为空");
            }
            Integer roleId = normalizeRoleId(request.getRoleId());
            String username = trimToNull(request.getUsername());
            String studentNo = trimToNull(request.getStudentNo());
            String normalizedIdentity = normalizeIdentityValue(request.getIdentity(), null);

            if ((roleId == 3 || roleId == 4) && containsIdentity(normalizedIdentity, "班团骨干")) {
                roleId = 3;
            }
            enforceOperatorCanManageTarget(operatorRole, roleId);

            if (roleId == 4 || roleId == 3) {
                if (studentNo == null) {
                    throw new RuntimeException("学生或骨干必须提供学号");
                }
                if (username == null) {
                    username = studentNo;
                }
            } else {
                if (username == null) {
                    throw new RuntimeException("用户名不能为空");
                }
            }

            validateUserFields(studentNo, trimToNull(request.getGrade()), trimToNull(request.getMajor()), normalizedIdentity, roleId);

            if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
                throw new RuntimeException("用户名已存在");
            }
            if (studentNo != null
                    && userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStudentNo, studentNo)) > 0) {
                throw new RuntimeException("学号已存在");
            }

            User user = new User();
            user.setUsername(username);
            user.setRoleId(roleId);
            user.setRealName(trimToNull(request.getRealName()));
            user.setStudentNo(studentNo);
            user.setGrade(trimToNull(request.getGrade()));
            user.setMajor(trimToNull(request.getMajor()));

            String defaultIdentity = "";
            if (roleId == 4) {
                defaultIdentity = "普通学生";
            } else if (roleId == 3) {
                defaultIdentity = "班团骨干";
            } else if (roleId == 2) {
                defaultIdentity = "管理老师";
            } else if (roleId == 1) {
                defaultIdentity = "学院领导";
            }
            user.setIdentity(normalizeIdentityValue(normalizedIdentity, defaultIdentity));

            user.setEmail(trimToNull(request.getEmail()));
            user.setPhone(encryptIfPresent(request.getPhone()));
            user.setIdCard(encryptIfPresent(request.getIdCard()));

            String password = trimToNull(request.getPassword());
            String rawPassword = password == null ? "123456" : password;
            user.setPassword(passwordEncoder.encode(rawPassword));

            userMapper.insert(user);
            auditLogService.success("CREATE_USER", String.valueOf(user.getId()));
            return toUserVO(userMapper.selectById(user.getId()));
        } catch (RuntimeException e) {
            auditLogService.failure("CREATE_USER", request == null ? "" : String.valueOf(request.getUsername()),
                    e.getMessage());
            throw e;
        }
    }

    @Transactional
    public UserVO updateUser(Long userId, UpdateUserRequest request, Long operatorId, Integer operatorRole) {
        try {
            if (userId == null) {
                throw new RuntimeException("缺少 userId");
            }
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }

            Integer newRoleId = request == null ? null : request.getRoleId();
            if (newRoleId != null) {
                newRoleId = normalizeRoleId(newRoleId);
                enforceOperatorCanManageTarget(operatorRole, newRoleId);
            } else {
                enforceOperatorCanManageTarget(operatorRole, user.getRoleId());
            }

            if (request != null) {
                if (request.getUsername() != null) {
                    String username = trimToNull(request.getUsername());
                    if (username == null) {
                        throw new RuntimeException("用户名不能为空");
                    }
                    if (!username.equals(user.getUsername())
                            && userMapper
                                    .selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
                        throw new RuntimeException("用户名已存在");
                    }
                    user.setUsername(username);
                }
                if (newRoleId != null) {
                    user.setRoleId(newRoleId);
                }
                if (request.getRealName() != null) {
                    user.setRealName(trimToNull(request.getRealName()));
                }
                if (request.getStudentNo() != null) {
                    String studentNo = trimToNull(request.getStudentNo());
                    if (studentNo != null && !studentNo.equals(user.getStudentNo())
                            && userMapper.selectCount(
                                    new LambdaQueryWrapper<User>().eq(User::getStudentNo, studentNo)) > 0) {
                        throw new RuntimeException("学号已存在");
                    }
                    user.setStudentNo(studentNo);
                }
                if (request.getGrade() != null) {
                    user.setGrade(trimToNull(request.getGrade()));
                }
                if (request.getMajor() != null) {
                    user.setMajor(trimToNull(request.getMajor()));
                }
                if (request.getIdentity() != null) {
                    String normalizedIdentity = normalizeIdentityValue(request.getIdentity(), user.getIdentity());
                    user.setIdentity(normalizedIdentity);
                    if (user.getRoleId() != null && (user.getRoleId() == 3 || user.getRoleId() == 4)) {
                        user.setRoleId(containsIdentity(normalizedIdentity, "班团骨干") ? 3 : 4);
                    }
                }
                if (request.getEmail() != null) {
                    user.setEmail(trimToNull(request.getEmail()));
                }
                if (request.getPhone() != null) {
                    user.setPhone(encryptIfPresent(request.getPhone()));
                }
                if (request.getIdCard() != null) {
                    user.setIdCard(encryptIfPresent(request.getIdCard()));
                }
                if (request.getPassword() != null) {
                    String raw = trimToNull(request.getPassword());
                    if (raw == null) {
                        user.setPassword("");
                    } else {
                        user.setPassword(passwordEncoder.encode(raw));
                    }
                }
                validateUserFields(user.getStudentNo(), user.getGrade(), user.getMajor(), user.getIdentity(), user.getRoleId());
            }

            userMapper.updateById(user);
            auditLogService.success("UPDATE_USER", String.valueOf(userId));
            return toUserVO(userMapper.selectById(userId));
        } catch (RuntimeException e) {
            auditLogService.failure("UPDATE_USER", String.valueOf(userId), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteUser(Long userId, Long operatorId, Integer operatorRole) {
        try {
            if (userId == null) {
                throw new RuntimeException("缺少 userId");
            }
            if (operatorId != null && operatorId.equals(userId)) {
                throw new RuntimeException("不能删除自己");
            }
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new RuntimeException("用户不存在");
            }
            enforceOperatorCanManageTarget(operatorRole, user.getRoleId());
            userMapper.deleteById(userId);
            auditLogService.success("DELETE_USER", String.valueOf(userId));
        } catch (RuntimeException e) {
            auditLogService.failure("DELETE_USER", String.valueOf(userId), e.getMessage());
            throw e;
        }
    }

    @Transactional
    public void deleteNotification(String id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification != null && isPushCategory(notification.getCategory())) {
            LambdaQueryWrapper<DeliveryLog> logWrapper = new LambdaQueryWrapper<>();
            logWrapper.eq(DeliveryLog::getTitle, notification.getTitle());
            if (!isBlank(notification.getPublishAt())) {
                logWrapper.eq(DeliveryLog::getSentAt, notification.getPublishAt());
            }
            deliveryLogMapper.delete(logWrapper);
        }
        notificationMapper.deleteById(id);
        auditLogService.success("DELETE_NOTIFICATION", id);
    }

    @Transactional
    public void deleteDeliveryLog(String id) {
        DeliveryLog log = deliveryLogMapper.selectById(id);
        if (log != null) {
            LambdaQueryWrapper<Notification> notificationWrapper = new LambdaQueryWrapper<>();
            notificationWrapper.eq(Notification::getTitle, log.getTitle());
            notificationWrapper.in(Notification::getCategory, List.of("推送", "精准推送"));
            if (!isBlank(log.getSentAt())) {
                notificationWrapper.eq(Notification::getPublishAt, log.getSentAt());
            }
            notificationMapper.delete(notificationWrapper);
        }
        deliveryLogMapper.deleteById(id);
        auditLogService.success("DELETE_DELIVERY_LOG", id);
    }

    @Transactional
    public ImportUsersResult importUsers(String fileName, List<ImportUserRow> rows, Long operatorId,
            Integer operatorRole) {
        String auditTarget = defaultIfBlank(fileName, "users");
        try {
            if (rows == null) {
                throw new RuntimeException("导入数据不能为空");
            }

            int total = rows.size();
            int success = 0;
            int failed = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < rows.size(); i++) {
                ImportUserRow row = rows.get(i);
                try {
                    // ===== 字段合法性校验 =====
                    String grade = row == null ? null : trimToNull(row.getGrade());
                    String major = row == null ? null : trimToNull(row.getMajor());
                    String identity = row == null ? null : trimToNull(row.getIdentity());
                    String studentNo = row == null ? null : row.getStudentNo();
                    Integer roleId = row == null ? null : row.getRoleId();

                    validateUserFields(trimToNull(studentNo), grade, major, identity, roleId == null ? 4 : roleId);
                    // ===== 结束校验 =====

                    CreateUserRequest req = new CreateUserRequest();
                    req.setUsername(row == null ? null : row.getUsername());
                    req.setPassword(row == null ? null : row.getPassword());
                    req.setRoleId(roleId);
                    req.setRealName(row == null ? null : row.getRealName());
                    req.setStudentNo(trimToNull(studentNo));
                    req.setGrade(grade);
                    req.setMajor(major);
                    req.setIdentity(identity);
                    req.setEmail(row == null ? null : row.getEmail());
                    req.setPhone(row == null ? null : row.getPhone());
                    req.setIdCard(row == null ? null : row.getIdCard());
                    createUser(req, operatorId, operatorRole);
                    success++;
                } catch (RuntimeException ex) {
                    failed++;
                    if (errors.size() < 30) {
                        errors.add("第 " + (i + 1) + " 行：" + ex.getMessage());
                    }
                }
            }

            ImportSession session = new ImportSession();
            session.setId("userimp-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
            session.setFileName(defaultIfBlank(fileName, "users.xlsx"));
            session.setTotalRows(total);
            session.setSuccessRows(success);
            session.setFailedRows(failed);
            session.setImportedAt(LocalDateTime.now().format(FORMATTER));
            session.setOperatorId(operatorId);
            importSessionMapper.insert(session);

            if (success > 0 || total == 0) {
                auditLogService.success("IMPORT_USERS", session.getId());
            } else {
                auditLogService.failure("IMPORT_USERS", session.getId(), "全部 " + total + " 行导入失败");
            }
            return ImportUsersResult.builder()
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
            auditLogService.failure("IMPORT_USERS", auditTarget, e.getMessage());
            throw e;
        }
    }

    public ImportUsersResult importUsersFromFile(MultipartFile file, Long operatorId, Integer operatorRole) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }
        String name = file.getOriginalFilename() == null ? "users.xlsx" : file.getOriginalFilename();
        List<ImportUserRow> rows;
        try {
            if (name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
                rows = parseCsvUsers(file);
            } else {
                rows = parseExcelUsers(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("读取文件失败");
        }
        return importUsers(name, rows, operatorId, operatorRole);
    }

    private List<User> listRecipients(PushFilter filter) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(User::getRoleId, 1).ne(User::getRoleId, 2);
        if (filter != null) {
            if (!isBlank(filter.getGrade()) && !"全部".equals(filter.getGrade())) {
                wrapper.eq(User::getGrade, filter.getGrade());
            }
            if (!isBlank(filter.getMajor()) && !"全部".equals(filter.getMajor())) {
                wrapper.eq(User::getMajor, filter.getMajor());
            }
        }
        return filterUsersByIdentity(userMapper.selectList(wrapper), filter == null ? null : filter.getIdentity());
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

    private void validateUserFields(String studentNo, String grade, String major, String identity, Integer roleId) {
        if (!isBlank(grade)) {
            String gradeTrimmed = trimToNull(grade);
            String normalizedGrade = normalizeGradeValue(gradeTrimmed);
            if (!VALID_GRADES.contains(normalizedGrade)) {
                throw new RuntimeException("年级「" + gradeTrimmed + "」不符合要求");
            }
        }
        if (!isBlank(major)) {
            String majorTrimmed = trimToNull(major);
            if (!VALID_MAJORS.contains(majorTrimmed)) {
                throw new RuntimeException("专业「" + majorTrimmed + "」没有在选项中");
            }
        }
        if (!isBlank(identity)) {
            normalizeIdentityValue(identity, null);
        }
        if (roleId != null && (roleId == 3 || roleId == 4)) {
            String studentNoTrimmed = trimToNull(studentNo);
            if (studentNoTrimmed == null) {
                throw new RuntimeException("学生或骨干必须提供学号");
            }
            if (!STUDENT_NO_PATTERN.matcher(studentNoTrimmed).matches()) {
                throw new RuntimeException("学号「" + studentNoTrimmed + "」不符合要求，学号必须为至少8位数字");
            }
        } else if (!isBlank(studentNo)) {
            String studentNoTrimmed = trimToNull(studentNo);
            if (!STUDENT_NO_PATTERN.matcher(studentNoTrimmed).matches()) {
                throw new RuntimeException("学号「" + studentNoTrimmed + "」不符合要求，学号必须为至少8位数字");
            }
        }
    }

    private String normalizeGradeValue(String grade) {
        String normalized = trimToNull(grade);
        if (normalized == null) {
            return null;
        }
        if (normalized.endsWith("级")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private List<User> filterUsersByIdentity(List<User> users, String identity) {
        if (users == null || users.isEmpty() || isBlank(identity) || "全部".equals(identity)) {
            return users == null ? List.of() : users;
        }
        return users.stream()
                .filter(user -> containsIdentity(user == null ? null : user.getIdentity(), identity))
                .collect(Collectors.toList());
    }

    private boolean containsIdentity(String rawIdentity, String targetIdentity) {
        if (isBlank(targetIdentity)) {
            return false;
        }
        return parseIdentityValues(rawIdentity).contains(targetIdentity.trim());
    }

    private String normalizeIdentityValue(String rawIdentity, String defaultIdentity) {
        List<String> identities = parseIdentityValues(rawIdentity);
        if (identities.isEmpty() && !isBlank(defaultIdentity)) {
            identities = parseIdentityValues(defaultIdentity);
        }
        for (String identity : identities) {
            if (!VALID_IDENTITIES.contains(identity)) {
                throw new RuntimeException("身份「" + identity + "」不符合要求，有效值：" + String.join("、", VALID_IDENTITIES));
            }
        }
        return String.join("、", identities);
    }

    private List<String> parseIdentityValues(String rawIdentity) {
        if (isBlank(rawIdentity)) {
            return new ArrayList<>();
        }
        List<String> identities = new ArrayList<>();
        for (String item : rawIdentity.split("[、,，;；/]")) {
            String normalized = trimToNull(item);
            if (normalized != null && !identities.contains(normalized)) {
                identities.add(normalized);
            }
        }
        return identities;
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

    private boolean isPushCategory(String category) {
        String normalized = trimToNull(category);
        return "推送".equals(normalized) || "精准推送".equals(normalized);
    }

    private UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }

        Long currentUserId = UserContext.getUserId();
        Integer currentRoleId = UserContext.getRoleId();
        // 管理员（角色1或2）或者是用户本人，可以看到完整信息
        boolean canSeeFullInfo = (currentRoleId != null && (currentRoleId == 1 || currentRoleId == 2))
                || (currentUserId != null && currentUserId.equals(user.getId()));

        String phone = null;
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            try {
                String decryptedPhone = encryptUtil.decrypt(user.getPhone());
                phone = canSeeFullInfo ? decryptedPhone : MaskUtil.maskPhone(decryptedPhone);
            } catch (Exception ignored) {
            }
        }
        String idCard = null;
        if (user.getIdCard() != null && !user.getIdCard().isEmpty()) {
            try {
                String decryptedIdCard = encryptUtil.decrypt(user.getIdCard());
                idCard = canSeeFullInfo ? decryptedIdCard : MaskUtil.maskIdCard(decryptedIdCard);
            } catch (Exception ignored) {
            }
        }
        return UserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roleId(user.getRoleId())
                .realName(user.getRealName())
                .studentNo(user.getStudentNo())
                .grade(user.getGrade())
                .major(user.getMajor())
                .identity(user.getIdentity())
                .email(user.getEmail())
                .phone(phone)
                .idCard(idCard)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private Integer normalizeRoleId(Integer roleId) {
        if (roleId == null) {
            return 4;
        }
        if (roleId < 1 || roleId > 4) {
            throw new RuntimeException("无效的角色");
        }
        return roleId;
    }

    private void enforceOperatorCanManageTarget(Integer operatorRole, Integer targetRole) {
        if (operatorRole == null || (operatorRole != 1 && operatorRole != 2)) {
            throw new RuntimeException("无权限访问");
        }
        if (operatorRole == 2 && targetRole != null && targetRole == 1) {
            throw new RuntimeException("无权限操作学院领导账号");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String encryptIfPresent(String value) {
        String raw = trimToNull(value);
        if (raw == null) {
            return null;
        }
        try {
            return encryptUtil.encrypt(raw);
        } catch (Exception e) {
            throw new RuntimeException("敏感信息加密失败");
        }
    }

    private List<ImportUserRow> parseExcelUsers(MultipartFile file) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new RuntimeException("Excel 表为空");
            }
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(sheet.getFirstRowNum());
            if (header == null) {
                throw new RuntimeException("缺少表头");
            }
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < header.getLastCellNum(); i++) {
                String key = normalizeHeader(formatter.formatCellValue(header.getCell(i)));
                if (key != null) {
                    idx.put(key, i);
                }
            }
            List<ImportUserRow> rows = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                ImportUserRow item = new ImportUserRow();
                item.setUsername(readCell(formatter, row, idx, "username"));
                item.setPassword(readCell(formatter, row, idx, "password"));
                item.setRoleId(parseInteger(readCell(formatter, row, idx, "roleId")));
                item.setRealName(readCell(formatter, row, idx, "realName"));
                item.setStudentNo(readCell(formatter, row, idx, "studentNo"));
                item.setGrade(readCell(formatter, row, idx, "grade"));
                item.setMajor(readCell(formatter, row, idx, "major"));
                item.setIdentity(readCell(formatter, row, idx, "identity"));
                item.setEmail(readCell(formatter, row, idx, "email"));
                item.setPhone(readCell(formatter, row, idx, "phone"));
                item.setIdCard(readCell(formatter, row, idx, "idCard"));

                if (trimToNull(item.getUsername()) == null && trimToNull(item.getStudentNo()) == null
                        && trimToNull(item.getRealName()) == null) {
                    continue;
                }
                rows.add(item);
            }
            return rows;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析 Excel 失败");
        }
    }

    private List<ImportUserRow> parseCsvUsers(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new RuntimeException("CSV 为空");
            }
            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                String key = normalizeHeader(headers[i]);
                if (key != null) {
                    idx.put(key, i);
                }
            }
            List<ImportUserRow> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = splitCsvLine(line);
                ImportUserRow item = new ImportUserRow();
                item.setUsername(readCsvCell(cells, idx, "username"));
                item.setPassword(readCsvCell(cells, idx, "password"));
                item.setRoleId(parseInteger(readCsvCell(cells, idx, "roleId")));
                item.setRealName(readCsvCell(cells, idx, "realName"));
                item.setStudentNo(readCsvCell(cells, idx, "studentNo"));
                item.setGrade(readCsvCell(cells, idx, "grade"));
                item.setMajor(readCsvCell(cells, idx, "major"));
                item.setIdentity(readCsvCell(cells, idx, "identity"));
                item.setEmail(readCsvCell(cells, idx, "email"));
                item.setPhone(readCsvCell(cells, idx, "phone"));
                item.setIdCard(readCsvCell(cells, idx, "idCard"));

                if (trimToNull(item.getUsername()) == null && trimToNull(item.getStudentNo()) == null
                        && trimToNull(item.getRealName()) == null) {
                    continue;
                }
                rows.add(item);
            }
            return rows;
        }
    }

    private String normalizeHeader(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "username", "用户", "用户名" -> "username";
            case "password", "密码" -> "password";
            case "role", "roleid", "角色", "角色id" -> "roleId";
            case "realname", "姓名" -> "realName";
            case "studentno", "student", "学号" -> "studentNo";
            case "grade", "年级" -> "grade";
            case "major", "专业" -> "major";
            case "identity", "身份" -> "identity";
            case "email", "邮箱" -> "email";
            case "phone", "手机号" -> "phone";
            case "idcard", "身份证" -> "idCard";
            default -> null;
        };
    }

    private String readCell(DataFormatter formatter, Row row, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null) {
            return null;
        }
        return trimToNull(formatter.formatCellValue(row.getCell(i)));
    }

    private String readCsvCell(String[] cells, Map<String, Integer> idx, String key) {
        Integer i = idx.get(key);
        if (i == null || i < 0 || i >= cells.length) {
            return null;
        }
        return trimToNull(cells[i]);
    }

    private Integer parseInteger(String value) {
        String v = trimToNull(value);
        if (v == null) {
            return null;
        }
        if ("学院领导".equals(v))
            return 1;
        if ("管理老师".equals(v))
            return 2;
        if ("骨干".equals(v) || "班团骨干".equals(v))
            return 3;
        if ("学生".equals(v) || "普通学生".equals(v))
            return 4;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String[] splitCsvLine(String line) {
        return line.split(",", -1);
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
        private String content;
        private String links;
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
    @NoArgsConstructor
    @AllArgsConstructor
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

    @lombok.Data
    public static class UserQuery {
        private Integer roleId;
        private String grade;
        private String major;
        private String keyword;
        private Boolean isStudentOnly;
    }

    @lombok.Builder
    @lombok.Data
    public static class UserVO {
        private Long id;
        private String username;
        private Integer roleId;
        private String realName;
        private String studentNo;
        private String grade;
        private String major;
        private String identity;
        private String email;
        private String phone;
        private String idCard;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @lombok.Data
    public static class CreateUserRequest {
        private String username;
        private String password;
        private Integer roleId;
        private String realName;
        private String studentNo;
        private String grade;
        private String major;
        private String identity;
        private String email;
        private String phone;
        private String idCard;
    }

    @lombok.Data
    public static class UpdateUserRequest {
        private String username;
        private String password;
        private Integer roleId;
        private String realName;
        private String studentNo;
        private String grade;
        private String major;
        private String identity;
        private String email;
        private String phone;
        private String idCard;
    }

    @lombok.Data
    public static class ImportUserRow {
        private String username;
        private String password;
        private Integer roleId;
        private String realName;
        private String studentNo;
        private String grade;
        private String major;
        private String identity;
        private String email;
        private String phone;
        private String idCard;
    }

    @lombok.Builder
    @lombok.Data
    public static class ImportUsersResult {
        private ImportSessionVO importSession;
        private List<String> errors;
        private String message;
    }

}
