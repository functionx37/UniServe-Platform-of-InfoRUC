-- =============================================
-- 信息学院学生综合服务平台 - 数据库初始化脚本
-- 适配：人大金仓 KingbaseES (兼容 PostgreSQL)
-- =============================================

SET search_path TO public;

-- =============================================
-- 1. 用户表 (users)
-- 四级角色：1-学院领导, 2-管理老师, 3-班团骨干, 4-普通学生
-- 敏感字段 phone, id_card 密文存储，password 哈希存储
-- =============================================
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,       -- 登录账号（学号或工号）
    password        VARCHAR(255) NOT NULL,              -- 加密密码或姓名（学生端当前无需密码）
    role_id         SMALLINT     NOT NULL DEFAULT 4,    -- 角色 1-4
    real_name       VARCHAR(50),                        -- 真实姓名
    student_no      VARCHAR(50) UNIQUE,                 -- 学号（学生专用）
    phone           VARCHAR(200),                       -- 加密存储
    id_card         VARCHAR(300),                       -- 加密存储
    grade           VARCHAR(10),                        -- 年级，如 2022
    major           VARCHAR(50),                        -- 专业
    identity        VARCHAR(30) DEFAULT '普通学生',     -- 身份标签
    email           VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.role_id IS '1-学院领导, 2-管理老师, 3-班团骨干, 4-普通学生';

-- =============================================
-- 2. 通知信息表 (notifications)
-- 供管理端发布与学生端查看
-- =============================================
CREATE TABLE notifications (
    id              VARCHAR(50) PRIMARY KEY,            -- policy-001 等
    title           VARCHAR(500) NOT NULL,
    category        VARCHAR(50)  NOT NULL,              -- 奖助/党建/竞赛/就业/实习/通知
    tag             VARCHAR(50)  NOT NULL,              -- 学生端展示用的标签
    grade           VARCHAR(20)  DEFAULT '全部',
    major           VARCHAR(50)  DEFAULT '全部',
    channel         VARCHAR(200) DEFAULT '站内消息',
    publish_at      VARCHAR(50),
    status          VARCHAR(20)  DEFAULT '待发布',
    content         TEXT,                               -- 通知正文
    links           TEXT,                               -- 相关链接，JSON 字符串
    created_by      BIGINT,                             -- 发布人ID
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE notifications IS '通知公告表';

-- =============================================
-- 3. 用户通知已读状态表 (notification_reads)
-- 记录每用户对每条通知的已读标记
-- =============================================
CREATE TABLE notification_reads (
    user_id         BIGINT NOT NULL,
    notification_id VARCHAR(50) NOT NULL,
    read_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, notification_id)
);

-- =============================================
-- 4. 党团流程阶段定义表 (party_stages)
-- 管理员维护标准流程
-- =============================================
CREATE TABLE party_stages (
    id              SERIAL PRIMARY KEY,
    stage_order     INT NOT NULL,                       -- 阶段顺序
    title           VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    default_time    VARCHAR(50),                        -- 预计时间
    status          VARCHAR(20) DEFAULT 'active'         -- 启用/停用
);

-- =============================================
-- 5. 用户党团进度表 (user_party_progress)
-- 记录学生在各个阶段的完成情况
-- =============================================
CREATE TABLE user_party_progress (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    stage_id        INT NOT NULL,                       -- 关联 party_stages.id
    completed       BOOLEAN DEFAULT FALSE,
    completed_at    TIMESTAMP,
    notes           TEXT
);

-- =============================================
-- 6. 事务申请表 (applications)
-- 学生提交的各种申请
-- =============================================
CREATE TABLE applications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type_key        VARCHAR(50) NOT NULL,               -- leave/enrollment_cert/political_cert
    type_label      VARCHAR(50),                        -- 类型中文名
    title           VARCHAR(200),
    status          SMALLINT DEFAULT 0,                 -- 0-待审, 1-通过, 2-驳回, 3-已撤回
    form            TEXT,                               -- 表单数据 JSON
    attachments     TEXT,                               -- 附件信息 JSON
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN applications.status IS '0-待审, 1-通过, 2-驳回, 3-已撤回';

-- =============================================
-- 7. 审批记录表 (approval_records)
-- 记录每一次审批动作（多级审批）
-- =============================================
CREATE TABLE approval_records (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL,
    approver_id     BIGINT,                             -- 审批人ID
    step_title      VARCHAR(100),                       -- 审批环节名称
    status          SMALLINT,                           -- 1-通过, 2-驳回
    opinion         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 8. 学业成绩表 (academic_records)
-- 存储解析后的课程成绩
-- =============================================
CREATE TABLE academic_records (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    course_name     VARCHAR(200),
    credits         DECIMAL(5,2),
    score           DECIMAL(5,2),
    category        VARCHAR(100),                       -- 课程类别（通识、专业必修等）
    semester        VARCHAR(20)
);

-- =============================================
-- 9. 成绩单上传记录表 (transcript_uploads)
-- =============================================
CREATE TABLE transcript_uploads (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255),
    file_id         VARCHAR(100),                       -- 文件存储标识
    uploaded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    parsed          BOOLEAN DEFAULT FALSE
);

-- =============================================
-- 10. 模板文件表 (templates)
-- =============================================
CREATE TABLE templates (
    id              VARCHAR(50) PRIMARY KEY,
    title           VARCHAR(200),
    scene           VARCHAR(50),
    file_type       VARCHAR(20),
    url             VARCHAR(500),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 11. 发送日志表 (delivery_logs) - 管理端推送记录
-- =============================================
CREATE TABLE delivery_logs (
    id              VARCHAR(50) PRIMARY KEY,
    title           VARCHAR(500),
    audience        VARCHAR(200),
    channels        VARCHAR(200),
    sent_at         VARCHAR(50),
    count           INT DEFAULT 0,
    status          VARCHAR(20),
    operator_id     BIGINT
);

-- =============================================
-- 12. 导入历史表 (import_sessions) - 管理端批量导入记录
-- =============================================
CREATE TABLE import_sessions (
    id              VARCHAR(50) PRIMARY KEY,
    file_name       VARCHAR(255),
    total_rows      INT DEFAULT 0,
    success_rows    INT DEFAULT 0,
    failed_rows     INT DEFAULT 0,
    imported_at     VARCHAR(50),
    operator_id     BIGINT
);

-- =============================================
-- 13. 审计日志表 (audit_logs)
-- =============================================
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    operator_id     BIGINT,
    action          VARCHAR(100),
    target          VARCHAR(200),
    result          VARCHAR(100),
    ip              VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 插入一条默认管理老师（密码暂时留空，接口层处理）
-- 实际项目建议通过程序初始化管理员账户
-- =============================================
-- INSERT INTO users(username, password, role_id, real_name) VALUES ('teacher01', '', 2, '李老师');
-- 插入默认管理老师，密码为 123456 的 BCrypt 密文
INSERT INTO users (username, password, role_id, real_name)
VALUES ('teacher01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8MNVz3GHkV5iEvGUD6FXQzC6ynu7y', 2, '李老师');