package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.*;
import cn.edu.ruc.info.entity.DeliveryLog;
import cn.edu.ruc.info.entity.ImportSession;
import cn.edu.ruc.info.entity.Notification;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.DeliveryLogMapper;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.mapper.NotificationMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private DeliveryLogMapper deliveryLogMapper;

    @Autowired
    private ImportSessionMapper importSessionMapper;

    @Autowired
    private UserMapper userMapper;

    public DashboardVO getDashboard(DashboardRequest request) {
        // 待发布通知数
        Long pendingCount = notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>().eq(Notification::getStatus, "待发布"));

        // 匹配学生数（根据筛选条件）
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

        // 发送日志总数
        Long deliveryCount = deliveryLogMapper.selectCount(null);

        // 最近导入成功率
        int successRate = 0;
        List<ImportSession> sessions = importSessionMapper.selectList(
                new LambdaQueryWrapper<ImportSession>().orderByDesc(ImportSession::getImportedAt).last("limit 1"));
        if (!sessions.isEmpty()) {
            ImportSession latest = sessions.get(0);
            if (latest.getTotalRows() != null && latest.getTotalRows() > 0) {
                successRate = (int) Math.round(
                        (latest.getSuccessRows() * 100.0) / latest.getTotalRows());
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
        List<Notification> notifications = notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>().orderByDesc(Notification::getPublishAt));

        return notifications.stream().map(n -> NotificationVO.builder()
                .id(n.getId())
                .title(n.getTitle())
                .category(n.getCategory())
                .grade(n.getGrade())
                .major(n.getMajor())
                .channel(n.getChannel())
                .publishAt(n.getPublishAt())
                .status(n.getStatus())
                .build()).collect(Collectors.toList());
    }

    public List<DeliveryLogVO> listDeliveryLogs() {
        List<DeliveryLog> logs = deliveryLogMapper.selectList(
                new LambdaQueryWrapper<DeliveryLog>().orderByDesc(DeliveryLog::getSentAt));

        return logs.stream().map(l -> DeliveryLogVO.builder()
                .id(l.getId())
                .title(l.getTitle())
                .audience(l.getAudience())
                .channels(l.getChannels())
                .sentAt(l.getSentAt())
                .count(l.getCount())
                .status(l.getStatus())
                .build()).collect(Collectors.toList());
    }

    public List<ImportSessionVO> listImportSessions() {
        List<ImportSession> sessions = importSessionMapper.selectList(
                new LambdaQueryWrapper<ImportSession>().orderByDesc(ImportSession::getImportedAt));

        return sessions.stream().map(s -> ImportSessionVO.builder()
                .id(s.getId())
                .fileName(s.getFileName())
                .totalRows(s.getTotalRows())
                .successRows(s.getSuccessRows())
                .failedRows(s.getFailedRows())
                .importedAt(s.getImportedAt())
                .build()).collect(Collectors.toList());
    }
}