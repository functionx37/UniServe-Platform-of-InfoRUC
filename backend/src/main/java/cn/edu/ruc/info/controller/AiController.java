package cn.edu.ruc.info.controller;

import cn.edu.ruc.info.common.Result;
import cn.edu.ruc.info.service.AiService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/ask")
    public Result<AiService.AskResponse> ask(@RequestBody AskRequest request) {
        try {
            return Result.success(aiService.ask(request.getQuestion()));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @Data
    public static class AskRequest {
        private String question;
    }
}
