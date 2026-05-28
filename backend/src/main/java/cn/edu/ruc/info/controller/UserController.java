package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.dto.LoginResponse;
import cn.edu.ruc.info.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public Result<LoginResponse.UserInfo> me() {
        try {
            LoginResponse.UserInfo info = userService.getUserInfo();
            return Result.success(info);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/profile")
    public Result<LoginResponse.UserInfo> updateProfile(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> body) {
        try {
            return Result.success(userService.updateProfile(body));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}