package cn.edu.ruc.info.config;

import cn.edu.ruc.info.entity.User;
import cn.edu.ruc.info.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // 检查是否已存在 teacher01
        User exist = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, "teacher01"));
        if (exist == null) {
            User admin = new User();
            admin.setUsername("teacher01");
            admin.setPassword(passwordEncoder.encode("123456")); // ← 用项目的 BCrypt 加密
            admin.setRoleId(2);
            admin.setRealName("李老师");
            userMapper.insert(admin);
            System.out.println("✅ 默认管理员 teacher01 已创建，密码 123456");
        } else {
            // 如果已存在，强制将其密码更新为正确的 BCrypt 密文
            exist.setPassword(passwordEncoder.encode("123456"));
            userMapper.updateById(exist);
            System.out.println("✅ 管理员 teacher01 密码已重置为 123456");
        }
    }
}