# 学生端小程序最终自测清单（miniprogram）

本文档基于当前真实代码结构整理（页面清单来自 `miniprogram/app.json`），用于老师验收与团队联调前的自测打勾。

## 0. 自测前准备

### 0.1 项目导入

- 使用微信开发者工具导入目录：`UniServe-Platform-of-InfoRUC/miniprogram`
- 确认可编译、无页面路径报错（pages/tabBar 均来自 `app.json`）

### 0.2 模式切换（Mock / 真实后端）

切换文件：`miniprogram/config/env.js`

- Mock 演示模式（当前默认）：
  - `USE_MOCK = true`
  - `BASE_URL = ""` 可保持为空
- 真实后端联调模式：
  - `USE_MOCK = false`
  - `BASE_URL = "https://<后端域名>/<前缀>"`（必须有值）

说明：

- Mock 模式下，列表数据/详情数据来自 `services/mock.js`，大部分流程可演示。
- Loading/弱网/部分空数据场景更适合在真实联调模式下测试（可通过网络面板限速实现可见 loading）。

### 0.3 通用检查维度（每个流程都要覆盖）

对每个流程，按以下 7 类场景逐项确认：

- [ ] 正常数据
- [ ] 空数据
- [ ] loading 状态
- [ ] error 状态
- [ ] 未登录访问
- [ ] 页面跳转（返回/切 tab/跳详情/重试）
- [ ] 敏感信息脱敏（手机号/身份证号/token 不展示明文、不打印）

### 0.4 未登录访问的统一验证方法

页面登录守卫由 `utils/storage.ensureLoggedIn()` 实现。验证步骤统一如下：

1. 在「Storage」清理本地 token（或点击个人中心“退出登录”）
2. 尝试进入任一需要登录页面（包括 tab 页）
3. 期望：自动跳转到登录页 `pages/login/index`

---

## 1. 页面清单（来自 app.json）

当前注册页面：

- `pages/index/index` 首页（tab）
- `pages/login/index` 登录
- `pages/ai-chat/index` 智能问答（tab）
- `pages/party-progress/index` 党团流程
- `pages/applications/index` 事务申请列表（tab）
- `pages/application-detail/index` 申请详情
- `pages/application-create/index` 新建申请
- `pages/academic/index` 学业概览
- `pages/transcript-upload/index` 成绩单上传
- `pages/academic-result/index` 学业分析结果
- `pages/notifications/index` 通知公告
- `pages/templates/index` 模板下载
- `pages/profile/index` 个人中心（tab）

---

## 2. 分流程自测清单

每个流程均标注：

- **Mock 已支持**：无需后端即可测试
- **需真实联调后再测**：依赖后端数据/权限/合法域名/文件服务等

---

### A. 登录（pages/login）

Mock 已支持：是（`POST /auth/login`）

- [ ] 正常数据：输入学号 + 姓名，登录成功；toast 提示；跳转到首页（switchTab）
- [ ] 空数据：不输入学号或姓名，点击登录应提示错误（页面错误态展示）
- [ ] loading：点击登录后按钮/页面出现 loading（避免重复提交）
- [ ] error：制造登录失败（Mock：缺字段；真实联调：后端返回 success=false），应展示错误态并可重试
- [ ] 未登录访问：不适用（此页为登录页）
- [ ] 页面跳转：登录成功后回到首页；再次进入小程序仍保持登录态（token 未清除）
- [ ] 敏感信息脱敏：
  - [ ] 不在任何日志/弹窗中输出 token
  - [ ] 登录接口返回中如包含手机号/身份证号，页面不应直接展示（个人中心会脱敏展示）

需真实联调后再测：

- [ ] 错误码/失败 message 是否与页面期望一致（会展示到错误组件中）
- [ ] token 过期/无效时的重新登录流程

---

### B. 首页（pages/index）

Mock 已支持：部分支持（依赖 `GET /user/me`、`GET /party/progress`、`GET /applications`）

- [ ] 正常数据：首页显示“当前登录：{姓名}”；待办卡片显示党团待办数、审批中申请数
- [ ] 空数据：
  - Mock：默认会返回数据，空数据建议在真实后端联调环境（新用户无记录）验证
- [ ] loading：首次进入/切回首页时显示 loading
- [ ] error：
  - Mock：难以模拟网络错误
  - 建议真实联调：断网/错误 BASE_URL 时应出现 error-view + retry
- [ ] 未登录访问：清除 token 后进入首页，自动跳转登录
- [ ] 页面跳转：各入口导航跳转到对应页面（问答/党团/事务/学业/通知/模板）
- [ ] 敏感信息脱敏：首页不显示手机号/身份证号；不打印 token

需真实联调后再测：

- [ ] 首页待办统计与后端真实规则一致（哪些算“审批中”/党团待办）

---

### C. 智能问答（pages/ai-chat）

Mock 已支持：是（`POST /ai/ask`）

- [ ] 正常数据：输入问题发送后出现回答；支持复制来源链接；可点击“相关问题”继续追问
- [ ] 空数据：
  - [ ] 初次进入无历史消息时显示空状态（empty-state）
  - [ ] 发送空问题应不发送/不新增消息
- [ ] loading：发送中出现 loading，且防重复提交（连点不会重复发送）
- [ ] error：模拟失败（真实联调可断网；Mock 可输入空问题触发失败提示），页面应展示 errorMsg 并支持 retry
- [ ] 未登录访问：清除 token 后进入问答（tab），应跳转登录
- [ ] 页面跳转：从首页进入/从 tab 切换进入均正常；返回后历史消息仍在（页面内存态）
- [ ] 敏感信息脱敏：回答/来源链接不应包含敏感信息；不打印 token

需真实联调后再测：

- [ ] 后端 AI 返回字段与前端期望一致（answer/sourceTitle/sourceUrl/relatedQuestions）
- [ ] 超长回答/富文本回答的展示策略（当前按文本展示）

---

### D. 党团流程进度（pages/party-progress）

Mock 已支持：是（`GET /party/progress`）

- [ ] 正常数据：展示整体进度百分比；时间线节点展示 done/current/todo；待办列表展示
- [ ] 空数据：
  - Mock：默认返回节点与待办；空数据建议在真实后端联调验证（无流程/无待办）
- [ ] loading：进入页面显示 loading
- [ ] error：真实联调断网/错误 BASE_URL 时显示 error-view 并可 retry
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从首页进入党团页；返回首页；重复进入仍正常
- [ ] 敏感信息脱敏：本模块不应出现手机号/身份证号；不打印 token

需真实联调后再测：

- [ ] 当前节点“呼吸灯”效果在不同机型/基础库版本表现一致（组件样式）
- [ ] 后端节点状态规则与前端 status 枚举一致（done/current/todo）

---

### E. 申请列表（pages/applications）

Mock 已支持：是（`GET /applications`）

- [ ] 正常数据：列表展示多条申请；状态筛选（全部/审批中/已通过/已驳回）切换后列表刷新
- [ ] 空数据：
  - Mock：默认每个状态都有数据；空数据建议真实后端联调（新用户无申请/筛选无结果）
- [ ] loading：切换筛选或进入页面出现 loading
- [ ] error：真实联调断网/错误 BASE_URL 时显示 error-view 并可 retry
- [ ] 未登录访问：清除 token 后进入（tab）应跳转登录
- [ ] 页面跳转：点击列表项进入申请详情；点击“新建申请”进入新建页；返回后列表刷新仍正常
- [ ] 敏感信息脱敏：列表不显示手机号/身份证号；不打印 token

需真实联调后再测：

- [ ] 后端 status 码到中文文案的映射一致（页面用“审批中/已通过/已驳回”）
- [ ] 分页/总数规则（若后端分页）

---

### F. 新建申请（pages/application-create）

Mock 已支持：是（`POST /applications`）

- [ ] 正常数据：
  - [ ] 选择“请假申请/在读证明/政治面貌证明”，填写表单后提交成功
  - [ ] 成功弹窗显示申请编号，并跳转到申请详情（redirectTo）
- [ ] 空数据：不填必填项提交，应展示错误态（error-view）并提示缺失项
- [ ] loading：提交中出现 loading，防重复提交
- [ ] error：
  - Mock：必填不完整会失败
  - 真实联调：后端返回 success=false 时应展示错误态
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从列表进入新建；提交成功跳详情；返回列表仍可看到新申请（真实后端需确认）
- [ ] 敏感信息脱敏：
  - [ ] 请假表单填写联系电话后，后续在“详情”展示必须脱敏
  - [ ] 不打印联系电话/身份证号/token

需真实联调后再测：

- [ ] 申请类型与字段清单是否以后端为准（如需动态化，后续再扩展）
- [ ] 附件上传/附件 URL 的真实生成方式（当前页面仅演示附件名）

---

### G. 申请详情（pages/application-detail）

Mock 已支持：是（`GET /applications/{id}`）

- [ ] 正常数据：展示申请编号/提交时间；表单字段；附件；审批时间线；审核意见；结果区（通过时可复制链接）
- [ ] 空数据：传入不存在 id（真实联调可直接访问不存在的 id），应展示错误或空状态（至少不能崩溃）
- [ ] loading：进入详情显示 loading
- [ ] error：真实联调断网/错误 BASE_URL 时显示 error-view 并可 retry
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从列表进入详情；详情返回列表；从新建跳转进入详情
- [ ] 敏感信息脱敏：
  - [ ] “联系电话”必须脱敏展示（****）
  - [ ] 不在 console 输出联系电话/token

需真实联调后再测：

- [ ] 后端审批流节点（approvals）与意见（approvalOpinions）的生成规则
- [ ] 结果文件下载/预览是否需鉴权、URL 是否签名、有效期等

---

### H. 成绩单上传（pages/transcript-upload）

Mock 已支持：是（Mock 上传）；需真实联调后再测：上传接口/合法域名/解析时序

- [ ] 正常数据：
  - [ ] 选择 pdf/xls/xlsx 文件，小于 10MB，上传成功弹窗提示可查看结果
- [ ] 空数据：未选择文件点击上传，不应触发上传（无报错/无异常）
- [ ] loading：上传中出现 loading，防重复点击
- [ ] error：
  - Mock：将文件名包含 `invalid` 或 `错误`，应提示“解析失败…”
  - 真实联调：断网/后端失败应展示 error-view
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从学业概览进入上传；上传成功跳学业分析结果；返回后仍可继续上传
- [ ] 敏感信息脱敏：不展示/不打印 token；如文件名含个人信息，注意不要在日志输出

需真实联调后再测：

- [ ] `wx.uploadFile` 返回体为 JSON 字符串且符合 `{ success, message, data }`
- [ ] 合法域名配置正确（upload 只能请求合法域名）
- [ ] 上传后解析是同步还是异步：若异步，需要后端提供任务状态接口（当前前端默认上传后可直接查看结果）

---

### I. 学业分析结果（pages/academic-result）

Mock 已支持：是（`GET /academic/analysis`）

- [ ] 正常数据：展示学分汇总、模块进度（progress-bar）、风险提示、必修缺失、建议列表
- [ ] 空数据：
  - Mock：若未上传/未解析，会返回失败；这属于“无数据”路径
  - 真实联调：可验证返回空模块/空建议时的展示
- [ ] loading：进入页面出现 loading
- [ ] error：
  - Mock：未上传成绩单时应提示“尚未上传或解析…”
  - 真实联调：断网/后端失败时显示 error-view 并可 retry
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从学业概览进入结果；从上传成功弹窗进入结果；在结果页可跳转去上传
- [ ] 敏感信息脱敏：分析结果中不应出现手机号/身份证号；不打印 token

需真实联调后再测：

- [ ] 后端模块字段与前端展示字段一致（modules/missingRequiredCourses/risks/suggestions）

---

### J. 通知公告（pages/notifications）

Mock 已支持：是（`GET /notifications`、`GET /notifications/{id}`、`POST /notifications/read`）

- [ ] 正常数据：列表展示；按标签筛选；点击展开加载详情；未读展开后标记已读
- [ ] 空数据：
  - Mock：每个标签都有数据；空数据建议真实联调（某标签无通知/无任何通知）
- [ ] loading：进入页面/切换标签出现 loading
- [ ] error：
  - 详情加载失败时 toast 提示（动作级错误）
  - 真实联调断网/请求失败时页面应显示 error-view 并可 retry
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从首页进入通知；筛选后返回再进入仍正常；复制链接到剪贴板成功
- [ ] 敏感信息脱敏：通知内容不应包含敏感信息；不打印 token

需真实联调后再测：

- [ ] 通知 content 是否为富文本（HTML/Markdown）还是纯文本（当前按文本展示）
- [ ] 已读状态是否按用户维度保存

---

### K. 模板下载（pages/templates）

Mock 已支持：是（`GET /templates`、`POST /templates/preview`、`POST /templates/download`）

- [ ] 正常数据：模板列表展示；点击“预览”弹窗并可复制链接；点击“下载”复制链接并提示成功
- [ ] 空数据：Mock 固定返回 3 条；空数据建议真实联调（无模板/筛选后无结果）
- [ ] loading：进入页面出现 loading
- [ ] error：
  - 预览/下载失败 toast 提示（动作级错误）
  - 真实联调断网/请求失败时页面应显示 error-view 并可 retry
- [ ] 未登录访问：清除 token 后进入应跳转登录
- [ ] 页面跳转：从首页进入模板；返回首页；复制链接到剪贴板成功
- [ ] 敏感信息脱敏：不打印 token；链接不应带明文敏感参数（如身份证号）

需真实联调后再测：

- [ ] 预览/下载链接是否需要签名与有效期
- [ ] 模板文件服务是否需要鉴权

---

### L. 个人中心（pages/profile）

Mock 已支持：部分支持（`GET /user/me`）；需真实联调后再测：真实用户信息字段对齐

- [ ] 正常数据：展示姓名/学号/学院/年级；展示手机号/身份证号（已脱敏）
- [ ] 空数据：后端返回 user 信息字段缺失时，页面展示为 `—`（不应崩溃）
- [ ] loading：首次拉取 user/me 时出现 loading
- [ ] error：真实联调断网/后端失败时显示错误态并可重试
- [ ] 未登录访问：清除 token 后进入（tab）应跳转登录
- [ ] 页面跳转：跳转到“事务申请/上传成绩单”等入口正常
- [ ] 敏感信息脱敏：
  - [ ] 手机号展示为 138****0000 形式
  - [ ] 身份证号展示为 1101********0000 形式
  - [ ] 不打印 token/手机号/身份证号

需真实联调后再测：

- [ ] 后端字段命名与前端一致（displayName/studentNo/major/grade/phone/idCard）
- [ ] 后端是否已做脱敏（前端仍会再脱敏一次）

---

### M. 退出登录（pages/profile 内动作）

Mock 已支持：是（纯前端清理 token/user）

- [ ] 正常数据：点击“退出登录”后清除本地 token/user，提示“已退出”，并回到首页（switchTab）
- [ ] 空数据：重复退出（无 token）不应报错
- [ ] loading：不适用（纯本地操作）
- [ ] error：不适用（纯本地操作）
- [ ] 未登录访问：退出后访问任一需要登录页面应跳转登录
- [ ] 页面跳转：退出后从 tab 进入任一受保护页面均会被拦截
- [ ] 敏感信息脱敏：退出过程中不打印 token

需真实联调后再测：

- [ ] token 过期后是否能被自动识别为未登录（当前逻辑为“本地是否有 token”）

---

## 3. 哪些项已被 Mock 覆盖 / 哪些需真实后端

### 3.1 Mock 已覆盖（可直接验收演示）

- 登录：`POST /auth/login`
- 获取用户：`GET /user/me`
- 智能问答：`POST /ai/ask`
- 党团流程：`GET /party/progress`
- 事务申请：`GET /applications`、`POST /applications`、`GET /applications/{id}`
- 学业概览/分析：`GET /academic/status`、`GET /academic/analysis`
- 成绩单上传：`POST /academic/transcript/upload`（Mock 上传/解析）
- 通知：`GET /notifications`、`GET /notifications/{id}`、`POST /notifications/read`
- 模板：`GET /templates`、`POST /templates/preview`、`POST /templates/download`、`GET /templates/{id}`

### 3.2 建议真实后端联调后重点补测

- 空数据场景：新用户无申请/无通知/无模板/无党团节点等（Mock 默认数据较满）
- 弱网与长 loading：Mock 返回较快，建议真实联调 + 网络限速验证 loading 体验
- token 过期/无效：后端鉴权失败时的前端体验（是否需要统一跳登录）
- 文件上传合法域名/鉴权下载：真实环境下 `wx.uploadFile`、模板/结果文件下载链接策略
- 富文本内容：通知/政策引用内容若为富文本需确认渲染策略

---

## 4. 需要人工确认的事项（联调前请统一口径）

1. **后端接口前缀与 BASE_URL**：是否需要 `/api` 前缀、是否网关转发、是否区分测试/生产域名
2. **鉴权头规范**：是否为 `Authorization: Bearer <token>`；token 过期策略与错误码/错误 message 规范
3. **状态枚举**：
   - 申请状态：是否直接返回中文（审批中/已通过/已驳回）还是返回 code（需 services 层映射）
   - 党团时间线节点：status 是否固定 `done/current/todo`
4. **分页策略**：申请列表/通知列表/模板列表是否分页（如分页需要新增 page/pageSize/total 协议）
5. **文件服务策略**：
   - 成绩单上传返回体格式（`wx.uploadFile` 的 `res.data` 必须是 JSON 字符串）
   - 上传后解析是同步还是异步（若异步需要解析状态查询接口）
   - 模板/申请结果文件的下载/预览是否需要签名 URL、是否有时效
6. **内容格式**：通知 content 是否为纯文本、HTML 或富文本节点（决定前端渲染方案）
7. **合法域名**：验收环境下微信小程序合法域名是否已配置（请求与上传都受限制）

