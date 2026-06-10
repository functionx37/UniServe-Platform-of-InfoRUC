package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.DeliveryLog;
import cn.edu.ruc.info.entity.Notification;
import cn.edu.ruc.info.mapper.DeliveryLogMapper;
import cn.edu.ruc.info.mapper.ImportSessionMapper;
import cn.edu.ruc.info.mapper.NotificationMapper;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.mapper.UserPartyProgressMapper;
import cn.edu.ruc.info.mapper.PartyStageMapper;
import cn.edu.ruc.info.util.EncryptUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceDeleteFlowTest {

    @Mock
    private NotificationMapper notificationMapper;
    @Mock
    private DeliveryLogMapper deliveryLogMapper;
    @Mock
    private ImportSessionMapper importSessionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EncryptUtil encryptUtil;
    @Mock
    private UserPartyProgressMapper userPartyProgressMapper;
    @Mock
    private PartyStageMapper partyStageMapper;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(
                notificationMapper,
                deliveryLogMapper,
                importSessionMapper,
                userMapper,
                auditLogService,
                passwordEncoder,
                encryptUtil,
                userPartyProgressMapper,
                partyStageMapper);
    }

    @Test
    void shouldDeleteLinkedNotificationWhenDeletingDeliveryLog() {
        DeliveryLog log = new DeliveryLog();
        log.setId("delivery-1");
        log.setTitle("推送标题");
        log.setSentAt("2026-05-29 21:00:00");
        when(deliveryLogMapper.selectById("delivery-1")).thenReturn(log);

        adminService.deleteDeliveryLog("delivery-1");

        InOrder inOrder = inOrder(notificationMapper, deliveryLogMapper, auditLogService);
        inOrder.verify(notificationMapper).delete(any());
        inOrder.verify(deliveryLogMapper).deleteById("delivery-1");
        inOrder.verify(auditLogService).success("DELETE_DELIVERY_LOG", "delivery-1");
    }

    @Test
    void shouldDeleteLinkedDeliveryLogWhenDeletingPushNotification() {
        Notification notification = new Notification();
        notification.setId("push-1");
        notification.setTitle("推送标题");
        notification.setCategory("推送");
        notification.setPublishAt("2026-05-29 21:00:00");
        when(notificationMapper.selectById("push-1")).thenReturn(notification);

        adminService.deleteNotification("push-1");

        InOrder inOrder = inOrder(deliveryLogMapper, notificationMapper, auditLogService);
        inOrder.verify(deliveryLogMapper).delete(any());
        inOrder.verify(notificationMapper).deleteById("push-1");
        inOrder.verify(auditLogService).success("DELETE_NOTIFICATION", "push-1");
    }

    @Test
    void shouldNotDeleteDeliveryLogWhenDeletingNormalNotification() {
        Notification notification = new Notification();
        notification.setId("policy-1");
        notification.setTitle("普通通知");
        notification.setCategory("通知");
        when(notificationMapper.selectById("policy-1")).thenReturn(notification);

        adminService.deleteNotification("policy-1");

        verify(deliveryLogMapper, never()).delete(any());
        verify(notificationMapper).deleteById("policy-1");
        verify(auditLogService).success("DELETE_NOTIFICATION", "policy-1");
    }
}
