package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.LoginResponse;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.util.EncryptUtil;
import cn.edu.ruc.info.util.MaskUtil;
import cn.edu.ruc.info.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EncryptUtil encryptUtil;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 获取当前登录用户信息，并脱敏手机、身份证
     */
    public LoginResponse.UserInfo getUserInfo() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
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

        return LoginResponse.UserInfo.builder()
                .id(user.getId())
                .roleId(user.getRoleId())
                .displayName(user.getRealName())
                .studentNo(user.getStudentNo())
                .grade(user.getGrade())
                .major(user.getMajor())
                .phone(phone)
                .idCard(idCard)
                .build();
    }

    @Transactional
    public LoginResponse.UserInfo updateProfile(Map<String, String> body) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new RuntimeException("未登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (body.containsKey("studentNo")) {
            user.setStudentNo(body.get("studentNo"));
        }
        if (body.containsKey("email")) {
            user.setEmail(body.get("email"));
        }
        if (body.containsKey("phone")) {
            String phone = body.get("phone");
            if (phone != null && !phone.contains("*")) { // 只有非脱敏格式才更新
                try {
                    user.setPhone(encryptUtil.encrypt(phone));
                } catch (Exception e) {
                    throw new RuntimeException("手机号加密失败");
                }
            }
        }
        if (body.containsKey("idCard")) {
            String idCard = body.get("idCard");
            if (idCard != null && !idCard.contains("*")) {
                try {
                    user.setIdCard(encryptUtil.encrypt(idCard));
                } catch (Exception e) {
                    throw new RuntimeException("身份证加密失败");
                }
            }
        }

        userMapper.updateById(user);
        auditLogService.success("UPDATE_PROFILE", String.valueOf(userId));
        return getUserInfo();
    }
}