-- ======================================================================
-- 审计服务数据库 (db_audit)
-- 职责：操作审计、敏感操作记录、安全日志
-- ======================================================================

-- 创建数据库
-- CREATE DATABASE db_audit WITH ENCODING = 'UTF8';

-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ======================================================================
-- 1. 审计日志表 (sys_audit_log) - 使用分区表提升性能
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID,                                    -- 关联 db_user.sys_user.id
    username VARCHAR(64),
    real_name VARCHAR(64),
    dept_id UUID,                                    -- 关联 db_org.sys_dept.id
    operation_type VARCHAR(32) NOT NULL,
    operation_module VARCHAR(64),
    operation_desc TEXT,
    request_uri VARCHAR(512),
    request_method VARCHAR(16),
    request_params JSONB,
    response_data JSONB,
    response_status SMALLINT,
    ip_address INET,
    location VARCHAR(128),
    user_agent TEXT,
    business_type VARCHAR(64),
    business_id VARCHAR(128),
    old_value JSONB,
    new_value JSONB,
    risk_level SMALLINT DEFAULT 1
        CONSTRAINT chk_log_risk CHECK (risk_level IN (1, 2, 3, 4)),
    status SMALLINT DEFAULT 1
        CONSTRAINT chk_log_status CHECK (status IN (0, 1)),
    error_msg TEXT,
    execute_time INTEGER,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, create_time)
) PARTITION BY RANGE (create_time);

-- 创建分区（按月）- 2025年
CREATE TABLE sys_audit_log_2025_01 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
CREATE TABLE sys_audit_log_2025_02 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
CREATE TABLE sys_audit_log_2025_03 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-03-01') TO ('2025-04-01');
CREATE TABLE sys_audit_log_2025_04 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
CREATE TABLE sys_audit_log_2025_05 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-05-01') TO ('2025-06-01');
CREATE TABLE sys_audit_log_2025_06 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-06-01') TO ('2025-07-01');
CREATE TABLE sys_audit_log_2025_07 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-07-01') TO ('2025-08-01');
CREATE TABLE sys_audit_log_2025_08 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-08-01') TO ('2025-09-01');
CREATE TABLE sys_audit_log_2025_09 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-09-01') TO ('2025-10-01');
CREATE TABLE sys_audit_log_2025_10 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
CREATE TABLE sys_audit_log_2025_11 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
CREATE TABLE sys_audit_log_2025_12 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- 2026年分区
CREATE TABLE sys_audit_log_2026_01 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE sys_audit_log_2026_02 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE sys_audit_log_2026_03 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE sys_audit_log_2026_04 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE sys_audit_log_2026_05 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE sys_audit_log_2026_06 PARTITION OF sys_audit_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

CREATE INDEX idx_log_user ON sys_audit_log(user_id);
CREATE INDEX idx_log_time ON sys_audit_log(create_time DESC);
CREATE INDEX idx_log_type ON sys_audit_log(operation_type);
CREATE INDEX idx_log_business ON sys_audit_log(business_type, business_id)
    WHERE business_type IS NOT NULL;
CREATE INDEX idx_log_risk ON sys_audit_log(risk_level) WHERE risk_level >= 3;
CREATE INDEX idx_log_request_params ON sys_audit_log USING GIN (request_params)
    WHERE request_params IS NOT NULL;

COMMENT ON TABLE sys_audit_log IS '操作审计日志表(按月分区)';
COMMENT ON COLUMN sys_audit_log.user_id IS '用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_audit_log.dept_id IS '部门ID(跨库关联db_org.sys_dept)';
COMMENT ON COLUMN sys_audit_log.operation_type IS '操作类型:LOGIN,LOGOUT,ADD,UPDATE,DELETE,QUERY,EXPORT,APPROVE';
COMMENT ON COLUMN sys_audit_log.risk_level IS '风险等级:1-低,2-中,3-高,4-极高';

-- ======================================================================
-- 2. 敏感操作日志表 (sys_sensitive_operation_log)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_sensitive_operation_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                           -- 关联 db_user.sys_user.id
    username VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL,
    operation_module VARCHAR(64),
    sensitive_data_type VARCHAR(50) NOT NULL,
    data_fingerprint VARCHAR(64),
    affected_count INTEGER DEFAULT 0,
    target_table VARCHAR(64),
    target_ids UUID[],
    operation_detail JSONB,
    approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    approval_id UUID,                                -- 关联 db_approval.sys_permission_approval.id
    risk_score SMALLINT DEFAULT 1
        CONSTRAINT chk_risk_score CHECK (risk_score BETWEEN 1 AND 10),
    ip_address INET,
    user_agent TEXT,
    device_fingerprint VARCHAR(128),
    location VARCHAR(128),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sol_user ON sys_sensitive_operation_log(user_id);
CREATE INDEX idx_sol_type ON sys_sensitive_operation_log(operation_type);
CREATE INDEX idx_sol_data_type ON sys_sensitive_operation_log(sensitive_data_type);
CREATE INDEX idx_sol_time ON sys_sensitive_operation_log(create_time DESC);
CREATE INDEX idx_sol_risk ON sys_sensitive_operation_log(risk_score) WHERE risk_score >= 7;
CREATE INDEX idx_sol_approval ON sys_sensitive_operation_log(approval_id)
    WHERE approval_required = TRUE;
CREATE INDEX idx_sol_fingerprint ON sys_sensitive_operation_log(data_fingerprint)
    WHERE data_fingerprint IS NOT NULL;
CREATE INDEX idx_sol_target ON sys_sensitive_operation_log USING GIN (target_ids)
    WHERE target_ids IS NOT NULL;

COMMENT ON TABLE sys_sensitive_operation_log IS '敏感操作日志表';
COMMENT ON COLUMN sys_sensitive_operation_log.user_id IS '用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_sensitive_operation_log.approval_id IS '审批ID(跨库关联db_approval.sys_permission_approval)';
COMMENT ON COLUMN sys_sensitive_operation_log.operation_type IS '操作类型:EXPORT,BULK_UPDATE,BULK_DELETE,DATA_DOWNLOAD,PERMISSION_CHANGE';
COMMENT ON COLUMN sys_sensitive_operation_log.sensitive_data_type IS '敏感数据类型:PERSONAL_INFO,FINANCIAL,MEDICAL,SECRET';
COMMENT ON COLUMN sys_sensitive_operation_log.risk_score IS '风险评分:1-10';

-- ======================================================================
-- 自动分区函数
-- ======================================================================
CREATE OR REPLACE FUNCTION create_audit_log_partition()
RETURNS VOID AS $$
DECLARE
    next_month DATE;
    partition_name TEXT;
    start_date DATE;
    end_date DATE;
BEGIN
    next_month := DATE_TRUNC('month', NOW()) + INTERVAL '1 month';
    partition_name := 'sys_audit_log_' || TO_CHAR(next_month, 'YYYY_MM');
    start_date := next_month;
    end_date := next_month + INTERVAL '1 month';

    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF sys_audit_log FOR VALUES FROM (%L) TO (%L)',
            partition_name, start_date, end_date
        );
        RAISE NOTICE 'Created partition: %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION create_audit_log_partition() IS '自动创建下月审计日志分区表';

-- ======================================================================
-- 审计统计视图
-- ======================================================================
CREATE OR REPLACE VIEW v_audit_daily_stats AS
SELECT
    DATE(create_time) as audit_date,
    operation_type,
    COUNT(*) as operation_count,
    COUNT(CASE WHEN status = 0 THEN 1 END) as failed_count,
    COUNT(CASE WHEN risk_level >= 3 THEN 1 END) as high_risk_count,
    AVG(execute_time) as avg_execute_time
FROM sys_audit_log
WHERE create_time >= NOW() - INTERVAL '30 days'
GROUP BY DATE(create_time), operation_type
ORDER BY audit_date DESC, operation_count DESC;

COMMENT ON VIEW v_audit_daily_stats IS '每日审计统计视图';

-- ======================================================================
-- 高风险操作告警视图
-- ======================================================================
CREATE OR REPLACE VIEW v_high_risk_operations AS
SELECT
    sol.id,
    sol.user_id,
    sol.username,
    sol.operation_type,
    sol.sensitive_data_type,
    sol.risk_score,
    sol.affected_count,
    sol.ip_address,
    sol.location,
    sol.create_time
FROM sys_sensitive_operation_log sol
WHERE sol.risk_score >= 7
ORDER BY sol.create_time DESC
LIMIT 100;

COMMENT ON VIEW v_high_risk_operations IS '高风险操作告警视图';