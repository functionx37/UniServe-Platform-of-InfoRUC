# B 成员交接说明

这份说明面向后续接手 B 线能力的同学，范围包括：

- 智能问答 / 知识库
- 成绩单解析
- 学业分析
- 证明生成与下载

## 1. 本次已补齐的能力

### 1.1 智能问答

已新增：

- `POST /ai/ask`
- `POST /admin/knowledge/documents`
- `GET /admin/knowledge/documents`
- `POST /admin/knowledge/rebuild`

对应代码：

- `backend/src/main/java/cn/edu/ruc/info/controller/AiController.java`
- `backend/src/main/java/cn/edu/ruc/info/controller/AdminKnowledgeController.java`
- `backend/src/main/java/cn/edu/ruc/info/service/AiService.java`
- `backend/src/main/java/cn/edu/ruc/info/service/KnowledgeBaseService.java`
- `backend/src/main/java/cn/edu/ruc/info/service/LlmClientService.java`

当前实现方式：

- 管理端上传 `PDF/TXT/MD` 政策文件
- 服务端抽取文本并切片
- 切片暂存在内存索引中
- 提问时先做片段检索，再拼接上下文调用大模型

### 1.2 成绩单解析

已新增/打通：

- `POST /academic/transcript/upload`

对应代码：

- `backend/src/main/java/cn/edu/ruc/info/controller/AcademicController.java`
- `backend/src/main/java/cn/edu/ruc/info/service/AcademicService.java`
- `backend/src/main/java/cn/edu/ruc/info/service/TranscriptParsingService.java`

当前支持：

- Excel 成绩单：`.xls` / `.xlsx`
- 文本型 PDF 成绩单：`.pdf`

解析成功后会：

- 写入 `transcript_uploads`
- 清空该学生旧的 `academic_records`
- 写入本次识别出的课程、学分、成绩、类别、学期

### 1.3 学业分析

已新增/打通：

- `GET /academic/status`
- `GET /academic/analysis`
- `POST /admin/curriculum/upload`
- `GET /admin/curriculum/latest`

对应代码：

- `backend/src/main/java/cn/edu/ruc/info/controller/AdminCurriculumController.java`
- `backend/src/main/java/cn/edu/ruc/info/service/CurriculumService.java`
- `backend/src/main/java/cn/edu/ruc/info/service/AcademicService.java`

当前逻辑：

- 管理端上传培养方案 `JSON/Excel`
- 服务端读取模块与课程要求
- 学生端分析时比对已修课程和培养方案
- 输出总学分、模块进度、缺失必修、风险提示、选课建议

### 1.4 证明生成

已新增/打通：

- 审批通过后自动生成证明
- `GET /files/proofs/{proofId}` 下载证明

对应代码：

- `backend/src/main/java/cn/edu/ruc/info/service/ProofGenerationService.java`
- `backend/src/main/java/cn/edu/ruc/info/controller/FileController.java`
- `backend/src/main/java/cn/edu/ruc/info/service/ApplicationService.java`

当前支持证明类型：

- `enrollment_cert` 在读证明
- `political_cert` 政治面貌证明

## 2. 新增的数据与配置

### 2.1 新增表

见 `backend/src/main/resources/sql/init.sql`：

- `knowledge_documents`
- `curriculum_files`
- `generated_proofs`

同时已有表被这条链路依赖：

- `academic_records`
- `transcript_uploads`
- `applications`
- `approval_records`

### 2.2 新增配置

见：

- `backend/src/main/resources/application.yml`
- `backend/.env.example`

关键环境变量：

- `APP_STORAGE_ROOT`
- `APP_KNOWLEDGE_DIR`
- `APP_CURRICULUM_DIR`
- `APP_TRANSCRIPT_DIR`
- `APP_PROOF_DIR`
- `LLM_BASE_URL`
- `LLM_API_TOKEN`
- `LLM_MODEL`

### 2.3 本地文件存储

目录初始化与文件落盘逻辑见：

- `backend/src/main/java/cn/edu/ruc/info/config/StorageInitializer.java`
- `backend/src/main/java/cn/edu/ruc/info/service/FileStorageService.java`
- `backend/src/main/java/cn/edu/ruc/info/util/StoragePathHelper.java`

## 3. 推荐联调顺序

1. 先执行最新 `init.sql`
2. 启动后端，确认 `data/` 目录已自动创建
3. 管理端先上传知识库文档
4. 管理端再上传培养方案
5. 学生端上传成绩单
6. 调 `/academic/status`、`/academic/analysis` 看分析结果
7. 创建 `enrollment_cert` 或 `political_cert` 申请
8. 管理端走审批通过
9. 用详情页返回的 `/files/proofs/{id}` 下载证明

## 4. 已知限制与后续优先事项

### 4.1 智能问答还不是真正的向量库 RAG

当前只是：

- 文本切片
- 进程内内存索引
- 关键词/子串匹配排序

还没有：

- embedding
- 向量数据库
- 语义检索

如果答辩或验收对“RAG/向量库”表述较严格，这一块需要继续补。

### 4.2 知识库索引没有在服务启动时自动重建

知识库文档虽然落库了，但索引只存在内存。

现状：

- 上传文档时会自动 `rebuild`
- 手动调 `/admin/knowledge/rebuild` 也可以恢复
- 但服务重启后不会自动把数据库中的文档重新建索引

后续建议：

- 在启动阶段自动执行一次 `rebuildIndex()`

### 4.3 培养方案 Excel 的“模块总学分”推导较粗

当前实现会把同一模块下 Excel 中所有课程学分直接累加为模块要求。

风险：

- 如果 Excel 中包含多个可选课程
- 模块学分可能被算大
- 学分缺口会被高估

后续建议：

- 培养方案改成显式配置“模块要求学分”
- 或者区分“课程池”和“模块最低要求”

### 4.4 PDF 成绩单解析依赖文本可提取

当前仅适配：

- 文本型 PDF
- 行格式较规整的成绩单

扫描版 PDF / 排版变化较大的 PDF 仍可能解析失败。

### 4.5 证明生成依赖系统中文字体

证明生成会在服务器上找中文字体文件。

如果部署环境缺少这些字体，审批通过时会直接生成失败。

后续建议：

- 把项目所需字体打包进镜像或仓库
- 不要依赖宿主机字体

## 5. 验收建议

建议重点验以下四条：

1. 服务重启后，未手动 rebuild 时 `/ai/ask` 是否还能命中知识库
2. 至少用一份真实培养方案 Excel 验证学分缺口是否合理
3. 至少用一份真实成绩单 PDF 和一份 Excel 跑解析
4. 在目标部署环境验证一次审批通过到 PDF 下载的全流程

## 6. 当前结论

这次 B 线工作已经从“预留坑位”推进到“主流程可联调”：

- 智能问答有后端接口、有知识库管理入口
- 成绩单上传后能入库并驱动学业分析
- 培养方案上传后能生成模块进度和缺口
- 证明生成已经接入审批流

但还没有达到“完全可验收无风险”：

- RAG 实现深度不足
- 缺少自动化测试
- 重启恢复、真实样本适配、部署字体依赖仍需补验
