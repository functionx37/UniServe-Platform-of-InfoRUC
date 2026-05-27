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
import cn.edu.ruc.info.util.EncryptUtil;
import cn.edu.ruc.info.util.MaskUtil;
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
    private final PasswordEncoder passwordEncoder;
    private final EncryptUtil encryptUtil;

    public AdminService(NotificationMapper notificationMapper,
            DeliveryLogMapper deliveryLogMapper,
            ImportSessionMapper importSessionMapper,
            UserMapper userMapper,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            EncryptUtil encryptUtil) {
        this.notificationMapper = notificationMapper;
        this.deliveryLogMapper = deliveryLogMapper;
        this.importSessionMapper = importSessionMapper;
        this.userMapper = userMapper;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.encryptUtil = encryptUtil;
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

    public List<AuditLog> listAuditLogs(String action, Integer limit) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (isBlank(action)) {
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
            enforceOperatorCanManageTarget(operatorRole, roleId);

            String username = trimToNull(request.getUsername());
            String studentNo = trimToNull(request.getStudentNo());

            if (roleId == 4) {
                if (studentNo == null) {
                    throw new RuntimeException("学号不能为空");
                }
                if (username == null) {
                    username = studentNo;
                }
            } else {
                if (username == null) {
                    throw new RuntimeException("用户名不能为空");
                }
            }

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
            user.setIdentity(defaultIfBlank(request.getIdentity(), roleId == 4 ? "普通学生" : ""));
            user.setEmail(trimToNull(request.getEmail()));
            user.setPhone(encryptIfPresent(request.getPhone()));
            user.setIdCard(encryptIfPresent(request.getIdCard()));

            String password = trimToNull(request.getPassword());
            if (roleId == 4) {
                user.setPassword(password == null ? "" : passwordEncoder.encode(password));
            } else {
                String raw = password == null ? "123456" : password;
                user.setPassword(passwordEncoder.encode(raw));
            }

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
                    user.setIdentity(trimToNull(request.getIdentity()));
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
        notificationMapper.deleteById(id);
        auditLogService.success("DELETE_NOTIFICATION", id);
    }

    @Transactional
    public void deleteDeliveryLog(String id) {
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
                    CreateUserRequest req = new CreateUserRequest();
                    req.setUsername(row == null ? null : row.getUsername());
                    req.setPassword(row == null ? null : row.getPassword());
                    req.setRoleId(row == null ? null : row.getRoleId());
                    req.setRealName(row == null ? null : row.getRealName());
                    req.setStudentNo(row == null ? null : row.getStudentNo());
                    req.setGrade(row == null ? null : row.getGrade());
                    req.setMajor(row == null ? null : row.getMajor());
                    req.setIdentity(row == null ? null : row.getIdentity());
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

            auditLogService.success("IMPORT_USERS", session.getId());
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

    private UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        String phone = null;
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            try {
                phone = MaskUtil.maskPhone(encryptUtil.decrypt(user.getPhone()));
            } catch (Exception ignored) {
            }
        }
        String idCard = null;
        if (user.getIdCard() != null && !user.getIdCard().isEmpty()) {
            try {
                idCard = MaskUtil.maskIdCard(encryptUtil.decrypt(user.getIdCard()));
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
