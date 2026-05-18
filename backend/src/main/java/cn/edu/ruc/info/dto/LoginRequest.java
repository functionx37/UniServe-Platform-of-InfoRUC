package cn.edu.ruc.info.dto;

import lombok.Data;

@Data
public class LoginRequest {
    // 管理端登录
    private String username;
    private String password;

    // 学生端登录
    private String studentNo;
    private String name;
}