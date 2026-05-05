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
}