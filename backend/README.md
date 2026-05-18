# UniServe Platform of InfoRUC

人大信息学院学生综合服务与党团管理平台。

## 技术栈
- 后端：Spring Boot 3.x + MyBatis-Plus + Kingbase V9
- 安全：JWT 认证, AES 字段加密, RBAC 四级权限

## 快速启动
1. 执行 `backend/src/main/resources/sql/init.sql` 初始化数据库。
2. 修改 `application.yml` 中的数据库连接信息。
3. 运行 `BackendApplication.java`。

## 测试账号
- 老师：`teacher01` / `123456`
- 学生：`20260001` / `张三` (学号+姓名登录)

详细接口说明见 `docs/api/API契约说明.md`。