# UniServe Platform of InfoRUC

人大信息学院学生综合服务与党团管理平台。

## 技术栈
- 后端：Spring Boot 3.x + MyBatis-Plus + PostgreSQL（默认本地开发）/ Kingbase V9（可切回）
- 安全：JWT 认证, AES 字段加密, RBAC 四级权限

## 快速启动
1. 准备 PostgreSQL 数据库 `student_db`，默认连接参数为：
   - `DB_DRIVER_CLASS_NAME=org.postgresql.Driver`
   - `DB_URL=jdbc:postgresql://localhost:5432/student_db`
   - `DB_USERNAME=postgres`
   - `DB_PASSWORD=123456`
2. 执行 `backend/src/main/resources/sql/init.sql` 初始化数据库。
3. 运行 `BackendApplication.java` 或执行 `./mvnw spring-boot:run`。

## 切回金仓
如需切回金仓，无需改代码，只需通过环境变量覆盖数据源配置，例如：

```bash
export DB_DRIVER_CLASS_NAME=com.kingbase8.Driver
export DB_URL=jdbc:kingbase8://localhost:54321/student_db
export DB_USERNAME=system
export DB_PASSWORD=123456
```

## 测试账号
- 老师：`teacher01` / `123456`
- 学生：`20260001` / `张三` (学号+姓名登录)

详细接口说明见 `docs/api/API契约说明.md`。
