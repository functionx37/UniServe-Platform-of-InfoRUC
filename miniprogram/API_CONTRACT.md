# 学生端小程序接口联调清单（API Contract）

本文档用于学生端微信小程序与后端联调对齐，内容基于当前 `miniprogram/services/` 与 `services/mock.js` 的真实实现整理。

## 0. 总览与联调原则

### 0.1 分层调用规则（必须遵守）

- 页面层（`pages/`）**不得**直接调用 `wx.request`
- 所有 HTTP 请求统一通过 [services/request.js](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/request.js) 的 `request()` 方法
- 文件上传统一通过 [services/file.js](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/file.js) 的 `uploadFile()/uploadTranscript()`
- Mock 字段与真实接口字段不一致时，应在 `services/` 层做映射/归一，不要把兼容逻辑扩散到页面层

### 0.2 Mock / 真实接口切换方式

切换点位于 [config/env.js](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/config/env.js)：

```js
const BASE_URL = ""   // 真实联调时填写，如 "https://api.xxx.com/api"
const USE_MOCK = true // true=Mock；false=真实接口
const TIMEOUT = 10000
```

- Mock 演示：`USE_MOCK = true`（不需要配置 `BASE_URL`）
- 真实联调：`USE_MOCK = false` 且 `BASE_URL` 必须有值，否则 `request()` 会返回配置错误

### 0.3 统一返回体约定

`services/request.js` 与 `services/file.js` 默认按如下格式解析后端返回：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

- 当 `success === true`：Promise resolve（页面拿到 `res.data`）
- 当 `success === false`：Promise reject（页面通过 `e.message` 读取错误提示）

### 0.4 鉴权与请求头

- token 存储：`utils/storage.js`（`setToken/getToken`）
- 注入方式：`Authorization: Bearer <token>`（由 `services/request.js` / `services/file.js` 自动注入）

需要后端确认：鉴权头是否使用 `Authorization` + `Bearer`，或是否需要改为其它 header（如 `token`、`X-Token`）。

---

## 1. 接口清单（按模块）

以下每个接口均包含：

- 模块
- service 方法名
- 建议接口路径（当前前端实际使用的路径）
- 请求方法
- 请求参数
- 期望返回字段（页面实际消费的字段）
- 当前 mock 字段（来自 `services/mock.js`）
- 需要后端确认的问题

---

## 2. 登录与用户（Auth / User）

### 2.1 登录

- 模块：登录
- service 方法：[auth.login](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/auth.js#L3-L13)
- 建议接口路径：`/auth/login`
- 请求方法：`POST`
- 请求参数（JSON）：
  - `studentNo: string` 学号（页面当前使用）
  - `name: string` 姓名（页面当前使用）
  - 兼容：mock 也支持 `username/password`（见下）
- 期望返回字段（`data`）：
  - `token: string` 登录 token（页面写入本地存储）
  - `userInfo: object` 用户信息（存在则缓存到本地）
- 当前 mock 字段（`services/mock.js`）：
  - 入参：`studentNo`（或 `username`）、`name`（或 `password`）
  - 出参 `data`：
    - `token`
    - `role`（字符串，仅展示/调试用）
    - `displayName`
    - `userInfo`：`{ id, roleId, displayName, studentNo, grade, major, phone, idCard }`
- 需要后端确认的问题：
  1. 登录入参到底是 `studentNo+name`，还是 `username+password`？如为后者，前端需在 `services/auth.js` 做入参映射以保持页面不改。
  2. token 字段名是否为 `token`？是否需要 refreshToken/过期时间？
  3. `userInfo` 是否直接返回？还是只返回 userId，需再调用 `/user/me` 获取？

### 2.2 获取当前用户信息

- 模块：个人中心/首页
- service 方法：[user.getMe](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/user.js#L3-L12)
- 建议接口路径：`/user/me`
- 请求方法：`GET`
- 请求参数：无
- 期望返回字段（`data`）：
  - `displayName: string`
  - `studentNo: string`
  - `grade: string`
  - `major: string`
  - `phone?: string`（页面展示需脱敏）
  - `idCard?: string`（页面展示需脱敏）
- 当前 mock 字段：
  - 返回 `data` 即 `db.user`
- 需要后端确认的问题：
  1. 后端是否会对 `phone/idCard` 做脱敏？即便后端脱敏，前端也会再脱敏展示（保证安全）。
  2. 用户字段命名：是否是 `studentNo/major/grade` 还是其它命名（如 `student_id/college/year`）？建议在 `services/user.js` 做字段归一。

---

## 3. 智能问答（AI）

### 3.1 提问

- 模块：智能问答
- service 方法：[ai.askQuestion](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/ai.js#L3-L15)
- 建议接口路径：`/ai/ask`
- 请求方法：`POST`
- 请求参数（JSON）：
  - `question: string`
- 期望返回字段（`data`）：
  - `answer: string` 回答正文
  - `sourceTitle?: string` 来源标题（官方文件/入口）
  - `sourceUrl?: string` 来源链接（页面支持复制）
  - `relatedQuestions?: string[]` 相关追问
- 当前 mock 字段：
  - 根据 `question` 关键词返回不同 answer/source/relatedQuestions
- 需要后端确认的问题：
  1. 后端返回字段是否为 `answer/sourceTitle/sourceUrl/relatedQuestions`？如不同需在 `services/ai.js` 归一。
  2. AI 回答是否需要返回“引用片段/段落编号/可信度”等扩展字段？（页面暂未消费，可后续增强）

---

## 4. 党团流程进度（Party）

### 4.1 获取党团流程进度

- 模块：党团流程
- service 方法：[party.getPartyProgress](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/party.js#L51-L61)（别名：`getProgress`）
- 建议接口路径：`/party/progress`
- 请求方法：`GET`
- 请求参数：无
- 期望返回字段（`data`，页面最终消费的是 services 归一后的结构）：
  - `currentStage: string` 当前阶段名称
  - `progressPercent: number` 进度百分比（0-100）
  - `nodes: Array<{ key: string, title: string, desc?: string, time?: string, status: "done"|"current"|"todo" }>`
  - `todos: Array<{ title: string, dueAt?: string, note?: string }>`
- 当前 mock 字段：
  - 直接返回上述结构（含 5 个节点 + 2 个 todo）
- 需要后端确认的问题：
  1. 后端是否直接提供 `nodes`（推荐）？若后端只提供 `standardPath/doneNodes/currentStage`，前端 `services/party.js` 也能做兼容归一（当前已支持一种“旧格式”归一逻辑）。
  2. 节点 status 枚举是否固定为 `done/current/todo`？（建议固定，避免页面判断复杂）

---

## 5. 事务申请（Applications）

### 5.1 申请列表（可筛选）

- 模块：事务申请
- service 方法：[application.listApplications](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/application.js#L3-L9)
- 建议接口路径：`/applications`
- 请求方法：`GET`
- 请求参数（query）：
  - `status?: string` 当前页面传入的筛选值：`全部/审批中/已通过/已驳回`
- 期望返回字段（`data` 为数组）：
  - `id: string`
  - `typeKey: string`（如 `leave/enrollment_cert/political_cert`）
  - `typeLabel: string`
  - `title: string`
  - `status: string`（页面按文案判断颜色：`审批中/已通过/已驳回`）
  - `createdAt: string`
- 当前 mock 字段：
  - 入参 `status` 过滤
  - 返回 summary 数组（不含 `form/content`）
- 需要后端确认的问题：
  1. `status` 字段是否返回中文文案还是枚举码（0/1/2）？建议在 `services/application.js` 归一为页面需要的展示文案。
  2. 是否需要分页：`page/pageSize/total`？当前页面一次性拉取列表。

### 5.2 申请详情

- 模块：事务申请
- service 方法：[application.getApplicationDetail](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/application.js#L11-L16)
- 建议接口路径：`/applications/{id}`
- 请求方法：`GET`
- 请求参数：路径参数 `id`
- 期望返回字段（`data`）：
  - `id, typeKey, typeLabel, title, status, createdAt`
  - `form: object`（按类型展示字段；其中 `contactPhone` 会脱敏展示）
  - `attachments: Array<{ name: string, url?: string }>`
  - `approvals: Array<{ key: string, title: string, desc?: string, time?: string, status: "done"|"current"|"todo", opinion?: string }>`
  - `approvalOpinions: Array<{ step: string, time?: string, opinion: string }>`（用于展示“审核意见”摘要）
  - `result?: { downloadable?: boolean, title?: string, url?: string } | null`
- 当前 mock 字段：
  - `approvals` 中可能带 `opinion`，mock 会汇总生成 `approvalOpinions`
  - `result` 在“已通过”示例中返回可下载文件信息
- 需要后端确认的问题：
  1. `approvals`（时间线节点）由后端直接返回还是前端组装？建议后端返回，前端只展示。
  2. `result.url` 是否需要走鉴权下载？若需要，建议提供可直接访问的短期签名 URL 或下载接口。

### 5.3 新建申请

- 模块：事务申请
- service 方法：[application.createApplication](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/application.js#L18-L24)
- 建议接口路径：`/applications`
- 请求方法：`POST`
- 请求参数（JSON）：
  - `typeKey: string`
  - `form: object`（不同申请类型字段不同）
  - `attachments?: Array<{ name: string, url?: string }>`
- 期望返回字段（`data`）：
  - `id: string` 新申请编号（页面会弹窗展示并跳转详情）
- 当前 mock 字段：
  - 对 `leave/enrollment_cert/political_cert` 做必填校验
  - 返回 `{ id }`
- 需要后端确认的问题：
  1. 申请类型枚举与字段清单：后端是否提供“类型配置接口”？当前前端类型写死为 3 种（如需动态化建议后续新增接口）。
  2. 附件上传与回填：真实联调时 `attachments.url` 如何生成？（建议走上传接口拿到可访问 URL/文件 id）

---

## 6. 学业概览与分析（Academic）

### 6.1 学业概览

- 模块：学业分析
- service 方法：[academic.getOverview](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/academic.js#L3-L21)（别名：`getStatus`）
- 建议接口路径：`/academic/status`
- 请求方法：`GET`
- 请求参数：无
- 期望返回字段（`data`）：
  - `transcript?: { fileId?: string, fileName?: string, uploadedAt?: string, parsed?: boolean }`
  - `totalCredits: number`
  - `earnedCredits: number`
  - `gapCredits: number`
  - `riskCount: number`
- 当前 mock 字段：
  - `transcript` 来自内存态 `academicState.transcript`
  - `totalCredits/earnedCredits/gapCredits/riskCount`
- 需要后端确认的问题：
  1. 学分字段命名是否一致（`totalCredits/earnedCredits/gapCredits`）？如不同需在 `services/academic.js` 映射。
  2. 是否需要返回更细的“风险点列表”而不仅是 `riskCount`？

### 6.2 学业分析结果

- 模块：学业分析
- service 方法：[academic.getAnalysis](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/academic.js#L10-L21)
- 建议接口路径：`/academic/analysis`
- 请求方法：`GET`
- 请求参数：无（当前实现依赖已上传成绩单的解析态）
- 期望返回字段（`data`）：
  - `transcript`（同上）
  - `totalCredits/earnedCredits/gapCredits`
  - `modules: Array<{ key: string, title: string, requiredCredits: number, earnedCredits: number, percent: number, gapCredits: number }>`
  - `missingRequiredCourses: Array<{ course: string, reason: string }>`
  - `risks: string[]`
  - `suggestions: string[]`
- 当前 mock 字段：
  - 若 `transcript.parsed !== true`，返回失败：`"尚未上传或解析成绩单，请先上传成绩单"`
  - `modules` 为前端可直接展示的结构（含 percent/gapCredits）
- 需要后端确认的问题：
  1. `modules` 是否由后端计算 percent/gapCredits？若后端仅返回 required/earned，前端也可计算，但建议后端统一输出，避免多端差异。
  2. `missingRequiredCourses` 的字段命名（`course/reason`）是否一致？

---

## 7. 文件上传（Transcript Upload）

### 7.1 上传成绩单

- 模块：成绩单上传
- service 方法：[file.uploadTranscript](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/file.js#L64-L77)
- 建议接口路径：`/academic/transcript/upload`
- 请求方法：`POST`（`multipart/form-data`，由 `wx.uploadFile` 发起）
- 请求参数：
  - 文件字段名：`file`（当前前端固定）
  - 文件内容：pdf/xls/xlsx（页面侧做扩展名与大小校验）
  - 额外 formData：当前未传（如后端需要学年/学期，可后续加）
- 期望返回字段（JSON 字符串，解析后为 `{ success, message, data }`）：
  - `data.fileId: string` 上传文件标识
  - 可选：`data.parsed: boolean`、`data.warnings: string[]`（页面当前未消费，可后续增强）
- 当前 mock 字段：
  - 若文件名包含 `invalid/错误`，返回失败：`解析失败：文件内容无法识别...`
  - 成功返回：`data: { fileId }`，并更新 `academicState.transcript = { fileId, fileName, uploadedAt, parsed: true }`
- 需要后端确认的问题：
  1. 上传接口返回体是否为 JSON 字符串且结构遵循 `{ success, message, data }`？（`wx.uploadFile` 的 `res.data` 为字符串，前端会 `JSON.parse`）
  2. 后端是否需要额外参数（学号、学年学期、培养方案版本等）？
  3. 上传后的解析是同步还是异步？若异步，建议提供解析任务 id 与查询接口（当前前端假设上传后可直接查看分析结果）。

---

## 8. 通知公告（Notifications）

### 8.1 通知列表（可筛选）

- 模块：通知公告
- service 方法：[notification.listNotifications](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/notification.js#L3-L30)
- 建议接口路径：`/notifications`
- 请求方法：`GET`
- 请求参数（query）：
  - `tag?: string` 页面传入：`全部/就业/实习/竞赛/党团/学业`
- 期望返回字段（`data` 为数组）：
  - `id: string`
  - `title: string`
  - `tag: string`
  - `publishAt: string`
  - `read: boolean`
- 当前 mock 字段：
  - 返回列表时会去掉 `content`（仅摘要），详情再取
- 需要后端确认的问题：
  1. `tag` 的枚举集合与命名是否一致？如后端用 code，应在 `services/notification.js` 映射。
  2. 是否分页：`page/pageSize/total`？

### 8.2 通知详情

- 模块：通知公告
- service 方法：[notification.getNotificationDetail](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/notification.js#L11-L16)
- 建议接口路径：`/notifications/{id}`
- 请求方法：`GET`
- 请求参数：路径参数 `id`
- 期望返回字段（`data`）：
  - `content: string`
  - `links?: Array<{ title: string, url: string }>`
  - 以及列表字段（`id/title/tag/publishAt/read`）
- 当前 mock 字段：
  - 返回完整通知对象（含 `content` 与 `links`）
- 需要后端确认的问题：
  1. `content` 是否为纯文本还是富文本/HTML？若为富文本，前端渲染方式需对齐（当前按文本展示）。

### 8.3 标记已读

- 模块：通知公告
- service 方法：[notification.markAsRead](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/notification.js#L18-L24)
- 建议接口路径：`/notifications/read`
- 请求方法：`POST`
- 请求参数（JSON）：
  - `id: string`
- 期望返回字段（`data`）：
  - `{ id: string }`
- 当前 mock 字段：
  - 将 `read` 置为 `true` 并返回 `{ id }`
- 需要后端确认的问题：
  1. 已读是否按用户维度存储？（建议后端按 userId+noticeId 记录）

---

## 9. 模板下载（Templates）

### 9.1 模板列表

- 模块：模板下载
- service 方法：[template.listTemplates](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/template.js#L3-L8)
- 建议接口路径：`/templates`
- 请求方法：`GET`
- 请求参数：无
- 期望返回字段（`data` 为数组）：
  - `id: string`
  - `title: string`
  - `scene: string`
  - `fileType: string`（如 pdf/docx）
  - `updatedAt: string`
  - `url?: string`（如后端直接给可访问地址）
- 当前 mock 字段：
  - 直接返回 `db.templates`
- 需要后端确认的问题：
  1. `url` 是否直接可访问？若需要鉴权下载，建议提供签名 URL 或下载接口。

### 9.2 模板详情（当前页面未使用，但 services 已提供）

- 模块：模板下载
- service 方法：[template.getTemplateDetail](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/template.js#L10-L15)
- 建议接口路径：`/templates/{id}`
- 请求方法：`GET`
- 请求参数：路径参数 `id`
- 期望返回字段（`data`）：同模板列表字段 + 可扩展描述字段（如 `desc`）
- 当前 mock 字段：
  - 查找 `db.templates` 返回单项
- 需要后端确认的问题：
  1. 是否真的需要“详情接口”？若不需要可在后续删除该方法以简化维护。

### 9.3 生成预览链接

- 模块：模板下载
- service 方法：[template.previewTemplate](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/template.js#L17-L23)
- 建议接口路径：`/templates/preview`
- 请求方法：`POST`
- 请求参数（JSON）：
  - `id: string`
- 期望返回字段（`data`）：
  - `id: string`
  - `url: string`（可复制打开）
  - `fileType?: string`
  - `title?: string`
- 当前 mock 字段：
  - 返回 `{ id, url, fileType, title }`
- 需要后端确认的问题：
  1. 预览链接是否有时效？是否需要签名与过期时间字段？

### 9.4 生成下载链接

- 模块：模板下载
- service 方法：[template.downloadTemplate](file:///home/ikoics/UniServe-Platform-of-InfoRUC/miniprogram/services/template.js#L25-L38)
- 建议接口路径：`/templates/download`
- 请求方法：`POST`
- 请求参数（JSON）：
  - `id: string`
- 期望返回字段（`data`）：同预览
- 当前 mock 字段：同预览
- 需要后端确认的问题：
  1. 下载是否需要鉴权？是否需要一次性下载 token？

---

## 10. 联调时的改动优先级（避免污染页面）

真实联调推荐按以下顺序推进，减少对页面代码的影响：

1. 修改 `config/env.js`：`USE_MOCK=false` 并设置 `BASE_URL`
2. 对齐后端返回体 `{ success, message, data }`
3. 逐个调整 `services/*.js`：
   - 路径/方法/参数名对齐
   - 字段映射与默认值补齐
4. 检查鉴权：
   - token 写入/读取一致
   - `Authorization: Bearer <token>` 是否符合后端要求
5. 最后才考虑修改页面：
   - 仅当 UI 展示需求发生变化且无法通过 service 映射解决时，再做最小范围改动

---

## 11. 变更记录（本次）

- 新增文档：`miniprogram/API_CONTRACT.md`

