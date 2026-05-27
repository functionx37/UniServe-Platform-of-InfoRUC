# 致 B 同学接手备忘录

## 待完成算法坑位
1. **RAG 问答**：预留 `POST /ai/ask`，需实现知识库检索。
2. **成绩单解析**：`AcademicService` 需接入 PDF/Excel 解析逻辑。
3. **学分计算**：完善 `getAnalysis`，将 Mock 逻辑替换为真实比对算法。

## 安全注意
- 管理端路由 `/admin/**` 已被拦截器保护。
- 敏感数据加解密请调用 `EncryptUtil`。