# API 契约说明

## 鉴权
- Header: `Authorization: Bearer <token>`

## 核心接口
- `POST /auth/login`: 登录
- `GET /user/me`: 个人信息
- `GET /applications`: 学生申请列表
- `POST /applications`: 提交申请
- `GET /party/progress`: 党团进度
- `GET /academic/analysis`: 学业分析 (Mock)
- `POST /academic/transcript/upload`: 成绩单上传
- `POST /admin/applications/{id}/audit`: 老师审批 (Action: pass/reject/withdraw)
- `POST /admin/dashboard`: 管理端仪表盘统计