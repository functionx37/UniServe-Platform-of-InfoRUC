# 学生端微信小程序（miniprogram）

本目录为「学院学生综合服务与党团管理平台」的**微信小程序学生端**前端工程，用于演示与联调学生侧核心流程。

> 说明：当前工程默认开启 Mock（`USE_MOCK=true`），可在后端未完成/未接入时完整演示；联调时只需切换 `config/env.js` 的配置并对齐 `services/` 字段映射，页面层尽量不改动。

---

## 一、学生端前端项目概述

学生端覆盖的主要功能模块如下：

- 登录与会话管理
- 首页：功能入口与待办汇总
- 智能问答：政策解读与官方链接引用
- 党团流程进度：时间线展示与“当前节点呼吸灯”效果
- 事务申请：申请列表、详情、新建申请、状态展示
- 成绩单上传与学业分析：文件校验/上传、解析结果与培养方案比对展示
- 通知公告：列表、筛选、已读/未读、详情展开
- 模板下载：模板列表、预览/下载链接复制
- 个人中心：用户信息展示、退出登录、敏感信息脱敏

当前阶段：

- 支持 Mock 数据演示（见 `services/mock.js`）
- 支持切换真实后端接口（配置见 `config/env.js`，网络封装见 `services/request.js`）

---

## 二、技术架构说明

### 1. 开发方式

- 微信小程序原生开发（WXML / WXSS / JS / JSON）
- 不引入复杂第三方 UI 框架，优先使用组件化与统一样式

### 2. 分层与职责

- `pages/`：页面层（路由页、交互与状态管理）
- `components/`：通用组件层（空/错/加载、卡片、时间线、进度条、上传等）
- `services/`：接口服务层（所有网络/Mock 入口；做字段归一与兼容映射）
- `utils/`：工具层（存储、脱敏、格式化、校验、常量）
- `config/`：配置层（Mock/真实接口开关、BaseURL、超时等）
- `assets/`：静态资源层（当前版本未建立该目录；如需图标/图片可按规范新增）
- `app.js / app.json / app.wxss`：小程序入口、路由注册、全局样式

### 3. 请求规范（重要）

- 页面层**不允许**直接调用 `wx.request`
- 所有 HTTP 请求统一通过 `services/request.js` 的 `request()` 方法
- Mock 与真实接口通过 `config/env.js` 的 `USE_MOCK / BASE_URL / TIMEOUT` 切换

---

## 三、目录结构说明

以下目录树基于当前仓库真实结构整理：

```text
miniprogram/
├─ app.js
├─ app.json
├─ app.wxss
├─ README.md
├─ config/
│  └─ env.js
├─ pages/
│  ├─ index/
│  ├─ login/
│  ├─ ai-chat/
│  ├─ party-progress/
│  ├─ applications/
│  ├─ application-detail/
│  ├─ application-create/
│  ├─ academic/
│  ├─ transcript-upload/
│  ├─ academic-result/
│  ├─ notifications/
│  ├─ templates/
│  └─ profile/
├─ components/
│  ├─ empty-state/
│  ├─ loading-view/
│  ├─ error-view/
│  ├─ status-card/
│  ├─ progress-bar/
│  ├─ timeline/
│  ├─ nav-card/
│  └─ file-uploader/
├─ services/
│  ├─ request.js
│  ├─ mock.js
│  ├─ auth.js
│  ├─ user.js
│  ├─ ai.js
│  ├─ party.js
│  ├─ application.js
│  ├─ academic.js
│  ├─ file.js
│  ├─ notification.js
│  └─ template.js
└─ utils/
   ├─ constants.js
   ├─ storage.js
   ├─ mask.js
   ├─ format.js
   └─ validator.js
```

关键目录说明：

- `pages/`：每个页面目录通常包含 `index.js / index.wxml / index.wxss / index.json`，负责业务交互与页面级状态（loading/empty/error）。
- `components/`：通用组件以 properties 驱动，页面通过 `usingComponents` 引用，避免重复造 UI 轮子。
- `services/`：统一维护接口路径、参数与字段映射；当 Mock 字段与后端字段不一致时，应在这里做适配。
- `utils/`：集中管理本地存储、脱敏、校验、格式化，避免页面散落重复逻辑。
- `config/env.js`：Mock/真实接口的唯一切换点（不要在页面写死 IP）。
- `app.json`：页面路由注册与 tabBar 配置；新增页面必须同步注册。

---

## 四、页面路由与功能说明

当前 `app.json` 注册页面如下（顺序与文件一致）：

| 路由 | 页面 | 用途 |
| --- | --- | --- |
| `pages/index/index` | 首页 | 展示登录用户、待办汇总、模块入口导航 |
| `pages/login/index` | 登录 | 学号/姓名登录；写入 token 与用户信息；失败展示错误态 |
| `pages/ai-chat/index` | 智能问答 | 提问、历史消息、快捷问题、引用来源链接与相似问题 |
| `pages/party-progress/index` | 党团流程 | 展示流程进度、时间线与待办提醒 |
| `pages/applications/index` | 事务申请列表 | 按状态筛选、进入详情、新建申请入口 |
| `pages/application-detail/index` | 申请详情 | 展示表单字段、时间线流转、附件、结果与状态 |
| `pages/application-create/index` | 新建申请 | 选择申请类型、填写表单、校验必填、提交后跳转详情 |
| `pages/academic/index` | 学业概览 | 学分概览、预警信息、入口跳转至上传与分析 |
| `pages/transcript-upload/index` | 成绩单上传 | 文件选择与校验、上传、提示进入分析结果 |
| `pages/academic-result/index` | 学业分析结果 | 模块进度、缺口原因、必修缺失、选课建议 |
| `pages/notifications/index` | 通知公告 | 标签筛选、已读/未读、展开详情与链接复制 |
| `pages/templates/index` | 模板下载 | 模板列表、预览/下载链接生成与复制 |
| `pages/profile/index` | 个人中心 | 用户信息展示（脱敏）、跳转入口、退出登录 |

tabBar（底部四入口）配置：

- 首页：`pages/index/index`
- 问答：`pages/ai-chat/index`
- 事务：`pages/applications/index`
- 我的：`pages/profile/index`

---

## 五、核心模块说明

### 1) 登录与会话管理

- 登录接口封装：`services/auth.js`（`/auth/login`）
- 本地会话存储：`utils/storage.js`（token、user）
- 登录态守卫：需要登录的页面在 `onLoad` 调用 `ensureLoggedIn()`；未登录会跳转 `pages/login/index`

建议维护方式：

- 登录成功后统一写入 `setToken(token)` 与 `setUser(userInfo)`
- 退出登录时调用 `clearToken()`、`clearUser()`，并回到首页/登录页

### 2) 首页功能入口与待办展示

- 首页聚合多个模块的“轻量摘要”，例如党团待办数量、审批中申请数量
- UI 以 `nav-card`（入口）+ `status-card`（摘要）为主

### 3) 智能问答模块

- 请求封装：`services/ai.js`（`/ai/ask`）
- 页面表现：历史消息列表、快捷问题、loading 防重复提交、错误重试
- 回答结构强调“官方来源优先”：支持展示 `sourceTitle/sourceUrl` 与 `relatedQuestions`

### 4) 党团流程进度模块（时间线 + 呼吸灯）

- 请求封装：`services/party.js`（`/party/progress`）
- 页面使用 `timeline` 组件展示节点：`done / current / todo`
- “当前节点呼吸灯”效果由 `timeline` 组件样式实现（页面只负责传入节点状态）

### 5) 事务申请模块（列表 / 详情 / 新建 / 状态）

- 列表：`pages/applications`，支持按状态筛选与进入详情
- 新建：`pages/application-create`，按类型渲染表单并做必填校验，提交成功后跳转详情
- 详情：`pages/application-detail`，展示表单字段、附件、审批时间线与结果

状态展示建议：

- 页面层显示用文案（如“审批中/已通过/已驳回”），字段归一与兼容处理放在 `services/application.js`

### 6) 成绩单上传与学业分析

- 上传页：`pages/transcript-upload`
  - 校验：文件大小/扩展名（pdf/xls/xlsx）
  - 上传：通过 `services/file.js` 统一封装（Mock 下走 `mockUploadFile`，真实环境下走 `wx.uploadFile`）
- 分析结果页：`pages/academic-result`
  - 展示模块进度（`progress-bar`）、缺口原因、必修缺失、选课建议等可解释信息

### 7) 通知公告与模板下载

- 通知：`services/notification.js` + `pages/notifications`
  - 列表筛选、展开加载详情、标记已读
- 模板：`services/template.js` + `pages/templates`
  - 预览/下载通过生成链接并复制到剪贴板（便于演示与后续接入文件服务）

### 8) 个人中心与敏感信息脱敏展示

- 个人中心：`pages/profile`
- 敏感字段（手机号、身份证号等）展示必须通过 `utils/mask.js` 处理（例如 `maskPhone/maskIdCard`）
- 严禁在 console 中输出 token/手机号/身份证号

---

## 六、网络请求与接口约定

### 1. 统一入口

- 所有请求统一经过 `services/request.js` 的 `request(options)`：
  - Mock 模式：转发到 `services/mock.js` 的 `mockRequest`
  - 真实模式：内部使用 `wx.request`

### 2. 后端返回体约定

默认遵循统一格式：

```json
{
  "success": true,
  "message": "操作成功",
  "data": {}
}
```

### 3. token 注入

- token 从本地存储读取（`utils/storage.js` 的 `getToken()`）
- `services/request.js` 会在请求头中注入：
  - `Authorization: Bearer <token>`

### 4. 页面层调用规范

- 页面层只调用 `services/` 暴露的方法，不直接写接口路径
- 联调真实后端时优先修改：
  1) `config/env.js`
  2) `services/` 的请求路径、参数、字段映射
- 不建议为了联调而大面积修改页面层字段与逻辑

---

## 七、Mock 数据说明

### 1. Mock 位置与作用

- Mock 数据集中管理在 `services/mock.js`
- 目标：后端未完成时，学生端仍可完整演示核心流程（登录→问答→党团→申请→上传→分析→通知/模板→个人中心）

### 2. 切换真实接口步骤

1. 修改 `config/env.js`：
   - `USE_MOCK = false`
   - `BASE_URL = "https://你的后端域名或本地代理地址"`
2. 确认后端接口返回体遵循 `{ success, message, data }`
3. 如果字段与 Mock 不一致：
   - 在 `services/` 做字段映射（例如归一字段名、补默认值）
   - 页面层尽量保持不变

### 3. 字段不一致的处理原则（重要）

- Mock 与真实接口字段不一致时，优先在 `services/` 统一“页面需要的数据形状”
- 不要把兼容逻辑扩散到多个页面，避免维护成本上升

---

## 八、组件复用说明

通用组件位于 `components/`，页面通过 `usingComponents` 引用：

- `empty-state`：空数据占位（列表为空、无历史消息等）
- `loading-view`：加载态（页面级 loading，避免白屏）
- `error-view`：错误态（提供 retry 回调）
- `status-card`：信息摘要卡（如首页待办、详情元信息等）
- `progress-bar`：进度条（学业模块进度展示）
- `timeline`：时间线（党团流程与申请审批流，支持 current 节点样式）
- `nav-card`：导航入口卡（首页模块入口复用）
- `file-uploader`：文件选择/删除统一封装（上传页、附件选择等）

复用原则：

- 新增页面的通用 UI 优先抽为组件，再在页面中组合使用
- 组件通过 properties 驱动，避免把业务逻辑写进组件

---

## 九、开发规范与对齐注意事项

请严格遵守以下约束（验收重点）：

1. 不要在页面中直接调用 `wx.request`，必须通过 `services/request.js`
2. 不要在页面中硬编码 mock 数据；Mock 统一放 `services/mock.js`
3. 不要在页面中写死后端 IP；联调只改 `config/env.js` 与 `services/`
4. 不要输出 token、手机号、身份证号等敏感信息到 console
5. 手机号、身份证号等敏感展示必须经过 `utils/mask.js` 脱敏
6. 新增页面必须同步注册到 `app.json`，否则可能导致编译/路由失败
7. 新增接口先写到 `services/`，页面只调用 service 方法
8. 新增公共 UI 优先抽象为 `components/`，避免页面复制粘贴
9. 修改接口字段时优先改 `services` 映射，不要大面积改页面
10. 保持学生端职责边界：不实现管理员/老师 PC 端审批功能

---

## 十、本地运行与微信开发者工具检查步骤

### 1) 导入项目

1. 打开微信开发者工具
2. 选择「导入项目」
3. 项目目录选择：仓库下的 `miniprogram/`
4. AppID：
   - 演示/开发可使用测试号 AppID（或无 AppID 模式）
   - 联调/真机能力（如部分接口、上传能力）建议使用项目 AppID

### 2) 编译前建议检查

- `miniprogram/app.json`：
  - pages 路径与实际页面目录一致
  - tabBar 的 pagePath 必须是 pages 中已注册页面
- `miniprogram/config/env.js`：
  - Mock 演示：`USE_MOCK=true`（无需配置 `BASE_URL`）
  - 联调真实后端：`USE_MOCK=false` 且 `BASE_URL` 有值

### 3) 调试方式

- 打开调试器：
  - Console：查看运行日志（注意不要打印敏感信息）
  - Network：查看请求（Mock 模式下请求在 request 层被拦截为 mock，不一定产生真实网络面板记录）

### 4) 常见问题排查

- 页面路径错误/白屏：
  - 优先检查 `app.json` pages 是否遗漏或拼写错误
- tabBar 无法切换：
  - 检查 tabBar 的 `pagePath` 是否在 pages 中注册
- 接口请求失败：
  - Mock 模式：检查 `services/mock.js` 是否覆盖对应路径
  - 真实模式：检查 `config/env.js` 的 `BASE_URL`、合法域名、网络代理
- 文件上传不可用：
  - Mock 模式：走 mock 上传逻辑，仅用于演示
  - 真实模式：需后端提供上传接口并配置合法域名；确认 `wx.uploadFile` 服务端返回符合约定

---

## 十一、自测流程（学生端）

建议按“正常/空/加载/错误/未登录/跳转/脱敏”维度逐项自测：

1. 登录
   - 正常登录成功、失败提示、未登录访问拦截跳转
2. 首页
   - 待办汇总正常/为空、加载与错误态、入口跳转正确
3. 智能问答
   - 提问→回答、引用来源链接复制、相关问题点击继续问、空历史与错误重试
4. 党团流程
   - 时间线展示、current 节点样式（呼吸灯）、待办为空/有数据、加载与错误态
5. 新建申请
   - 必填校验、提交成功提示与跳转详情、提交失败错误态
6. 查看申请详情
   - 不同状态展示（审批中/通过/驳回）、审批时间线、联系方式脱敏、附件展示
7. 上传成绩单
   - 文件格式/大小校验、上传成功提示、上传失败错误态
8. 学业分析结果
   - 模块进度/缺口原因/建议展示、空数据与错误态、入口回跳
9. 查看通知
   - 标签筛选、展开详情、已读标记、空数据/错误态
10. 下载模板
   - 列表展示、预览/下载链接复制、空数据/错误态
11. 个人中心
   - 信息展示、手机号/身份证号脱敏、缓存用户/接口拉取、入口跳转
12. 退出登录
   - 清理 token、返回首页后未登录访问拦截生效

---

## 十二、后续联调计划（Mock → 真实接口）

1. 向后端确认接口：路径、方法、参数、返回字段（遵循 `{ success, message, data }`）
2. 修改 `config/env.js`：
   - 配置 `BASE_URL`
   - 将 `USE_MOCK` 切换为 `false`
3. 逐个对齐 `services/` 字段映射（必要时做兼容与默认值）
4. 检查登录态与 token 注入：
   - 登录返回 token 字段与存储一致
   - 受保护页面 `onLoad` 守卫生效
5. 处理微信小程序合法域名配置：
   - 开发阶段可临时关闭校验（仅本地开发）
   - 验收/上线需将后端域名加入合法域名
6. 完成全流程验收：
   - 登录→问答→党团→申请→上传→分析→通知/模板→个人中心
