package cn.edu.ruc.info.dto;

import cn.edu.ruc.info.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String role; // 角色中文名
    private String displayName;
    private UserInfo userInfo;

    @Data
    @Builder
    public static class UserInfo {
        private Long id;
        private Integer roleId;
        private String displayName;
        private String studentNo;
        private String grade;
        private String major;
        private String phone; // 脱敏后的手机号
        private String idCard; // 脱敏后的身份证号
    }
}