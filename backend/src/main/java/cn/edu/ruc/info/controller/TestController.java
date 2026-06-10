package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/hello")
    public Result<String> hello() {
        // 使用你刚才写的 Result 类返回数据
        return Result.success("你好！人大信息学院后台服务已就绪。");
    }

    @GetMapping("/split-app")
    public Result<String> splitApp() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("../admin-frontend/src/App.tsx");
            String content = new String(java.nio.file.Files.readAllBytes(path));
            int len = content.length();
            java.nio.file.Files.write(java.nio.file.Paths.get("../app1.txt"), content.substring(0, len/4).getBytes());
            java.nio.file.Files.write(java.nio.file.Paths.get("../app2.txt"), content.substring(len/4, len/2).getBytes());
            java.nio.file.Files.write(java.nio.file.Paths.get("../app3.txt"), content.substring(len/2, len*3/4).getBytes());
            java.nio.file.Files.write(java.nio.file.Paths.get("../app4.txt"), content.substring(len*3/4, len).getBytes());
            return Result.success("OK");
        } catch(Exception e) {
            return Result.error(e.getMessage());
        }
    }
}