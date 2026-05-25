package cn.edu.ruc.info.service;

import cn.edu.ruc.info.entity.AuditLog;
import cn.edu.ruc.info.mapper.AuditLogMapper;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuditLogService {
    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void success(String action, String target) {
        write(action, target, "SUCCESS");
    }

    public void failure(String action, String target, String reason) {
        String result = reason == null || reason.isBlank() ? "FAIL" : ("FAIL: " + truncate(reason, 80));
        write(action, target, result);
    }

    private void write(String action, String target, String result) {
        AuditLog log = new AuditLog();
        log.setOperatorId(UserContext.getUserId());
        log.setAction(action);
        log.setTarget(target);
        log.setResult(result);
        log.setIp(resolveClientIp());
        auditLogMapper.insert(log);
    }

    private String resolveClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
