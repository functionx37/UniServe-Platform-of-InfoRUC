-- =============================================
-- 信息学院学生综合服务平台 - Kingbase V9 完整初始化
-- 包含所有建表语句及基础数据
-- =============================================

SET search_path TO public;

-- 1. 用户表 (users)
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    role_id         SMALLINT     NOT NULL DEFAULT 4,
    real_name       VARCHAR(50),
    student_no      VARCHAR(50) UNIQUE,
    phone           VARCHAR(200),
    id_card         VARCHAR(300),
    grade           VARCHAR(10),
    major           VARCHAR(50),
    identity        VARCHAR(30) DEFAULT '普通学生',
    email           VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN users.role_id IS '1-学院领导, 2-管理老师, 3-骨干, 4-学生';

-- 2. 通知信息表 (notifications)
CREATE TABLE notifications (
    id              VARCHAR(50) PRIMARY KEY,
    title           VARCHAR(500) NOT NULL,
    category        VARCHAR(50)  NOT NULL,
    tag             VARCHAR(50)  NOT NULL,
    grade           VARCHAR(20)  DEFAULT '全部',
    major           VARCHAR(50)  DEFAULT '全部',
    channel         VARCHAR(200) DEFAULT '站内消息',
    publish_at      VARCHAR(50),
    status          VARCHAR(20)  DEFAULT '待发布',
    content         TEXT,
    links           TEXT,
    created_by      BIGINT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. 用户通知已读状态表 (notification_reads)
CREATE TABLE notification_reads (
    user_id         BIGINT NOT NULL,
    notification_id VARCHAR(50) NOT NULL,
    read_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, notification_id)
);

-- 4. 党团流程阶段定义表 (party_stages)
CREATE TABLE party_stages (
    id              SERIAL PRIMARY KEY,
    stage_order     INT NOT NULL,
    title           VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    default_time    VARCHAR(50),
    status          VARCHAR(20) DEFAULT 'active'
);

-- 5. 用户党团进度表 (user_party_progress)
CREATE TABLE user_party_progress (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    stage_id        INT NOT NULL,
    completed       BOOLEAN DEFAULT FALSE,
    completed_at    TIMESTAMP,
    notes           TEXT
);

-- 6. 事务申请表 (applications)
CREATE TABLE applications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    type_key        VARCHAR(50) NOT NULL,
    type_label      VARCHAR(50),
    title           VARCHAR(200),
    status          SMALLINT DEFAULT 0,
    form            TEXT,
    attachments     TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON COLUMN applications.status IS '0-待审, 1-通过, 2-驳回, 3-已撤回';

-- 7. 审批记录表 (approval_records)
CREATE TABLE approval_records (
    id              BIGSERIAL PRIMARY KEY,
    application_id  BIGINT NOT NULL,
    approver_id     BIGINT,
    step_title      VARCHAR(100),
    status          SMALLINT,
    opinion         TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. 学业成绩表 (academic_records)
CREATE TABLE academic_records (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    course_name     VARCHAR(200),
    credits         DECIMAL(5,2),
    score           DECIMAL(5,2),
    category        VARCHAR(100),
    semester        VARCHAR(20)
);

-- 9. 成绩单上传记录表 (transcript_uploads)
CREATE TABLE transcript_uploads (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    file_name       VARCHAR(255),
    file_id         VARCHAR(100),
    file_path       VARCHAR(500),
    uploaded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    parsed          BOOLEAN DEFAULT FALSE,
    parse_message   VARCHAR(500)
);

-- 10. 模板文件表 (templates)
CREATE TABLE templates (
    id              VARCHAR(50) PRIMARY KEY,
    title           VARCHAR(200),
    scene           VARCHAR(50),
    file_type       VARCHAR(20),
    url             VARCHAR(500),
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10.1 知识库文档表 (knowledge_documents)
CREATE TABLE knowledge_documents (
    id              VARCHAR(50) PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(20) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    source_url      VARCHAR(500),
    active          BOOLEAN DEFAULT TRUE,
    uploaded_by     BIGINT,
    uploaded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10.1.1 知识库切片表 (knowledge_chunks)
CREATE TABLE knowledge_chunks (
    id              VARCHAR(50) PRIMARY KEY,
    document_id     VARCHAR(50) NOT NULL,
    chunk_index     INT NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_id, chunk_index)
);

-- 10.1.2 知识库切片向量表 (knowledge_chunk_embeddings)
CREATE TABLE knowledge_chunk_embeddings (
    chunk_id        VARCHAR(50) PRIMARY KEY,
    model           VARCHAR(100) NOT NULL,
    dim             INT NOT NULL,
    embedding       TEXT NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10.2 培养方案文件表 (curriculum_files)
CREATE TABLE curriculum_files (
    id              VARCHAR(50) PRIMARY KEY,
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(20) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    version         VARCHAR(100),
    active          BOOLEAN DEFAULT TRUE,
    uploaded_by     BIGINT,
    uploaded_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10.3 证明文件表 (generated_proofs)
CREATE TABLE generated_proofs (
    id              VARCHAR(50) PRIMARY KEY,
    application_id  BIGINT NOT NULL,
    user_id         BIGINT NOT NULL,
    proof_type      VARCHAR(50) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_path       VARCHAR(500) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 11. 发送日志表 (delivery_logs)
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

-- 12. 导入历史表 (import_sessions)
CREATE TABLE import_sessions (
    id              VARCHAR(50) PRIMARY KEY,
    file_name       VARCHAR(255),
    total_rows      INT DEFAULT 0,
    success_rows    INT DEFAULT 0,
    failed_rows     INT DEFAULT 0,
    imported_at     VARCHAR(50),
    operator_id     BIGINT
);

-- 13. 审计日志表 (audit_logs)
CREATE TABLE audit_logs (
    id              BIGSERIAL PRIMARY KEY,
    operator_id     BIGINT,
    action          VARCHAR(100),
    target          VARCHAR(200),
    result          VARCHAR(100),
    ip              VARCHAR(50),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- [初始数据 DML]
-- 教师账号
INSERT INTO users (username, password, role_id, real_name)
VALUES ('teacher01', '', 2, '李老师')
ON CONFLICT (username) DO NOTHING;

-- 学生账号
INSERT INTO users (username, student_no, real_name, role_id, grade, major)
VALUES ('20260001', '20260001', '张三', 4, '2026', '信息学院')
ON CONFLICT (username) DO NOTHING;

-- 党团阶段
INSERT INTO party_stages (stage_order, title, description, default_time) VALUES
(1, '提交入党申请书', '提交申请书并完成信息登记', '2026-03-12'),
(2, '确定入党积极分子', '组织考察与材料审核', '2026-04-08'),
(3, '参加党课培训', '按要求完成党课学习与测评', ''),
(4, '确定发展对象', '公示与组织审查', ''),
(5, '接收为预备党员', '支部大会讨论与上级审批', '')
ON CONFLICT DO NOTHING;

-- 模板数据
INSERT INTO templates (id, title, scene, file_type, url) VALUES
('tpl-001', '在读证明申请模板', '在读证明', 'pdf', 'https://example.com/template/enrollment'),
('tpl-002', '请假申请表', '请假申请', 'docx', 'https://example.com/template/leave'),
('tpl-003', '思想汇报模板', '党团材料', 'docx', 'https://example.com/template/party')
ON CONFLICT DO NOTHING;
