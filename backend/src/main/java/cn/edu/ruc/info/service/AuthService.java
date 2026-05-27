package cn.edu.ruc.info.service;

import cn.edu.ruc.info.dto.LoginRequest;
import cn.edu.ruc.info.dto.LoginResponse;
import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.UserMapper;
import cn.edu.ruc.info.util.EncryptUtil;
import cn.edu.ruc.info.util.JwtUtil;
import cn.edu.ruc.info.util.MaskUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private EncryptUtil encryptUtil;

    public LoginResponse login(LoginRequest request) {
        // 判断登录方式：有 password 则为管理端，否则为学生端
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            return adminLogin(request);
        } else {
            return studentLogin(request);
        }
    }

    private LoginResponse adminLogin(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        if (username == null || username.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        if (user.getRoleId() != 1 && user.getRoleId() != 2) {
            throw new RuntimeException("非管理员角色，无法登录管理端");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRoleId());
        return buildLoginResponse(user, token);
    }

    private LoginResponse studentLogin(LoginRequest request) {
        String studentNo = (request.getStudentNo() != null && !request.getStudentNo().isBlank())
                ? request.getStudentNo().trim()
                : (request.getUsername() != null ? request.getUsername().trim() : null);
        String name = request.getName();
        if (studentNo == null || studentNo.isBlank()) {
            throw new RuntimeException("学号不能为空");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getStudentNo, studentNo));
        if (user == null) {
            // 如果不存在，可以自动创建或者抛出异常；这里先选择抛异常，提示联系管理员导入
            throw new RuntimeException("该学号尚未在系统中注册，请联系管理员");
        }
        // 比对姓名（忽略前后空格）
        if (name == null || !name.trim().equals(user.getRealName())) {
            throw new RuntimeException("姓名与学号不匹配");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getRoleId());
        return buildLoginResponse(user, token);
    }

    private LoginResponse buildLoginResponse(User user, String token) {
        // 生成角色中文名
        String roleCn = switch (user.getRoleId()) {
            case 1 -> "学院领导";
            case 2 -> "管理老师";
            case 3 -> "班团骨干";
            case 4 -> "学生";
            default -> "未知";
        };

        // 解密并脱敏手机号、身份证号
        String phone = null;
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            try {
                phone = encryptUtil.decrypt(user.getPhone());
                phone = MaskUtil.maskPhone(phone);
            } catch (Exception ignored) {
            }
        }
        String idCard = null;
        if (user.getIdCard() != null && !user.getIdCard().isEmpty()) {
            try {
                idCard = encryptUtil.decrypt(user.getIdCard());
                idCard = MaskUtil.maskIdCard(idCard);
            } catch (Exception ignored) {
            }
        }

        LoginResponse.UserInfo userInfo = LoginResponse.UserInfo.builder()
                .id(user.getId())
                .roleId(user.getRoleId())
                .displayName(user.getRealName())
                .studentNo(user.getStudentNo())
                .grade(user.getGrade())
                .major(user.getMajor())
                .phone(phone)
                .idCard(idCard)
                .build();

        return LoginResponse.builder()
                .token(token)
                .role(roleCn)
                .displayName(user.getRealName())
                .userInfo(userInfo)
                .build();
    }
}