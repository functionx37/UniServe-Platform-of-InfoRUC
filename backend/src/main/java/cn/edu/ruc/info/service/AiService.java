package cn.edu.ruc.info.service;

import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmClientService llmClientService;

    public AiService(KnowledgeBaseService knowledgeBaseService, LlmClientService llmClientService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.llmClientService = llmClientService;
    }

    public AskResponse ask(String question) {
        if (!StringUtils.hasText(question)) {
            throw new RuntimeException("问题不能为空");
        }
        List<KnowledgeBaseService.KnowledgeChunk> chunks = knowledgeBaseService.search(question, 4);
        if (chunks.isEmpty()) {
            throw new RuntimeException("未找到可依据的政策材料，请先上传相关政策文档");
        }

        KnowledgeBaseService.KnowledgeChunk top = chunks.get(0);
        String context = chunks.stream()
                .map(chunk -> "【来源：" + chunk.getDocumentTitle() + "】\n" + chunk.getText())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");

        String systemPrompt = """
                你是学院政策问答助手。
                只能依据给定政策片段回答，不允许补充片段中没有出现的制度细节。
                如果片段不足以回答，就明确说明“未在已上传政策中找到直接依据”。
                答案尽量简洁正式。
                """;
        String userPrompt = "问题：" + question + "\n\n政策片段：\n" + context;
        String answer = llmClientService.chat(systemPrompt, userPrompt);
        if (!StringUtils.hasText(answer)) {
            answer = "未在已上传政策中找到直接依据，请补充更具体的问题或上传相关政策。";
        }

        return AskResponse.builder()
                .answer(answer.trim())
                .sourceTitle(top.getDocumentTitle())
                .sourceUrl(top.getSourceUrl() == null ? "" : top.getSourceUrl())
                .relatedQuestions(buildRelatedQuestions(question))
                .build();
    }

    private List<String> buildRelatedQuestions(String question) {
        return List.of(
                question + " 适用于哪些对象？",
                question + " 需要提交什么材料？",
                question + " 办理时限和入口是什么？");
    }

    @Data
    @Builder
    public static class AskResponse {
        private String answer;
        private String sourceTitle;
        private String sourceUrl;
        private List<String> relatedQuestions;
    }
}
