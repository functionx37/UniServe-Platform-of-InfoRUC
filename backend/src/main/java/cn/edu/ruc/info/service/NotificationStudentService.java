package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.Notification;
import cn.edu.ruc.info.entity.NotificationRead;
import cn.edu.ruc.info.mapper.NotificationMapper;
import cn.edu.ruc.info.mapper.NotificationReadMapper;
import cn.edu.ruc.info.util.UserContext;
import lombok.Builder;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NotificationStudentService {

    @Autowired
    private NotificationMapper notificationMapper;
    @Autowired
    private NotificationReadMapper notificationReadMapper;

    public List<NotificationVO> listNotifications(String tag) {
        List<Notification> notifications;
        if ("全部".equals(tag) || tag == null) {
            notifications = notificationMapper.selectList(null);
        } else {
            notifications = notificationMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Notification>()
                            .eq(Notification::getTag, tag));
        }

        // 获取当前用户已读记录
        Long userId = UserContext.getUserId();
        Set<String> readIds = new HashSet<>();
        if (userId != null) {
            List<NotificationRead> reads = notificationReadMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationRead>()
                            .eq(NotificationRead::getUserId, userId));
            reads.forEach(r -> readIds.add(r.getNotificationId()));
        }

        return notifications.stream().map(n -> NotificationVO.builder()
                .id(n.getId())
                .title(n.getTitle())
                .tag(n.getTag())
                .publishAt(n.getPublishAt())
                .read(readIds.contains(n.getId()))
                .build()).collect(Collectors.toList());
    }

    public NotificationDetailVO getDetail(String id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null)
            throw new RuntimeException("通知不存在");

        // 解析 links JSON（暂作字符串处理）
        List<Link> links = new ArrayList<>();
        // 简单处理：假设 links 为 JSON 数组字符串，如 [{"title":"xxx","url":"xxx"}]
        // 这里不解析，直接返回空，后续可优化
        return NotificationDetailVO.builder()
                .id(n.getId())
                .title(n.getTitle())
                .tag(n.getTag())
                .publishAt(n.getPublishAt())
                .content(n.getContent())
                .links(links)
                .build();
    }

    public void markAsRead(String notificationId) {
        Long userId = UserContext.getUserId();
        if (userId == null)
            throw new RuntimeException("未登录");

        // 检查是否已存在
        NotificationRead exist = notificationReadMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationRead>()
                        .eq(NotificationRead::getUserId, userId)
                        .eq(NotificationRead::getNotificationId, notificationId));
        if (exist == null) {
            NotificationRead nr = new NotificationRead();
            nr.setUserId(userId);
            nr.setNotificationId(notificationId);
            notificationReadMapper.insert(nr);
        }
    }

    // 内部 VO
    @Data
    @Builder
    public static class NotificationVO {
        private String id;
        private String title;
        private String tag;
        private String publishAt;
        private boolean read;
    }

    @Data
    @Builder
    public static class NotificationDetailVO {
        private String id;
        private String title;
        private String tag;
        private String publishAt;
        private String content;
        private List<Link> links;
    }

    @Data
    @Builder
    public static class Link {
        private String title;
        private String url;
    }
}