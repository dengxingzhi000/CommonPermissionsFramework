-- ======================================================================
-- 通知服务数据库 (db_notify)
-- 职责：消息通知、邮件/短信发送记录
-- ======================================================================

-- 创建数据库
-- CREATE DATABASE db_notify WITH ENCODING = 'UTF8';

-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ======================================================================
-- 1. 通知审计表 (sys_notification_audit)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_notification_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id VARCHAR(128),                       -- 业务关联ID
    user_id UUID,                                    -- 关联 db_user.sys_user.id
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subject VARCHAR(256),
    username VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    template_code VARCHAR(64),
    content TEXT,
    variables JSONB,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    next_retry_time TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notify_ref ON sys_notification_audit(reference_id)
    WHERE reference_id IS NOT NULL;
CREATE INDEX idx_notify_user ON sys_notification_audit(user_id)
    WHERE user_id IS NOT NULL;
CREATE INDEX idx_notify_channel ON sys_notification_audit(channel);
CREATE INDEX idx_notify_status ON sys_notification_audit(status);
CREATE INDEX idx_notify_time ON sys_notification_audit(created_at DESC);
CREATE INDEX idx_notify_retry ON sys_notification_audit(next_retry_time)
    WHERE status = 'PENDING' AND next_retry_time IS NOT NULL;
CREATE INDEX idx_notify_variables ON sys_notification_audit USING GIN (variables)
    WHERE variables IS NOT NULL;

COMMENT ON TABLE sys_notification_audit IS '通知发送审计表';
COMMENT ON COLUMN sys_notification_audit.user_id IS '接收用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_notification_audit.channel IS '通知渠道:EMAIL,SMS,WECHAT,DINGTALK,FEISHU,PUSH';
COMMENT ON COLUMN sys_notification_audit.status IS '发送状态:PENDING,SENT,FAILED,CANCELLED';
COMMENT ON COLUMN sys_notification_audit.variables IS '模板变量(JSONB格式)';

-- ======================================================================
-- 2. 通知模板表 (sys_notification_template)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_notification_template (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code VARCHAR(64) NOT NULL UNIQUE,
    template_name VARCHAR(128) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    subject_template VARCHAR(256),
    content_template TEXT NOT NULL,
    variables_schema JSONB,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_template_status CHECK (status IN (0, 1)),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_template_code ON sys_notification_template(template_code) WHERE NOT deleted;
CREATE INDEX idx_template_channel ON sys_notification_template(channel) WHERE NOT deleted;

COMMENT ON TABLE sys_notification_template IS '通知模板表';
COMMENT ON COLUMN sys_notification_template.channel IS '通知渠道:EMAIL,SMS,WECHAT,DINGTALK,FEISHU,PUSH';
COMMENT ON COLUMN sys_notification_template.variables_schema IS '变量定义Schema(JSONB格式)';

-- ======================================================================
-- 3. 用户通知偏好表 (sys_user_notification_preference)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_user_notification_preference (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                           -- 关联 db_user.sys_user.id
    notification_type VARCHAR(64) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    quiet_hours_start TIME,
    quiet_hours_end TIME,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_user_type_channel UNIQUE (user_id, notification_type, channel)
);

CREATE INDEX idx_pref_user ON sys_user_notification_preference(user_id);
CREATE INDEX idx_pref_type ON sys_user_notification_preference(notification_type);

COMMENT ON TABLE sys_user_notification_preference IS '用户通知偏好表';
COMMENT ON COLUMN sys_user_notification_preference.user_id IS '用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_user_notification_preference.notification_type IS '通知类型:APPROVAL,SECURITY,SYSTEM,MARKETING';
COMMENT ON COLUMN sys_user_notification_preference.quiet_hours_start IS '免打扰开始时间';
COMMENT ON COLUMN sys_user_notification_preference.quiet_hours_end IS '免打扰结束时间';

-- ======================================================================
-- 注意：update_time 字段由 MyBatis-Plus MetaObjectHandler 自动填充
-- 参见：common/data/.../MyMetaObjectHandler.java
-- ======================================================================

-- ======================================================================
-- 通知统计视图
-- ======================================================================
CREATE OR REPLACE VIEW v_notification_stats AS
SELECT
    channel,
    status,
    DATE(created_at) as notify_date,
    COUNT(*) as total_count,
    AVG(CASE WHEN status = 'SENT' THEN
        EXTRACT(EPOCH FROM (sent_at - created_at))
    END) as avg_send_time_seconds
FROM sys_notification_audit
WHERE created_at >= NOW() - INTERVAL '30 days'
GROUP BY channel, status, DATE(created_at)
ORDER BY notify_date DESC;

COMMENT ON VIEW v_notification_stats IS '通知统计视图';

-- ======================================================================
-- 待重试通知查询函数
-- ======================================================================
CREATE OR REPLACE FUNCTION get_pending_retry_notifications(p_limit INTEGER DEFAULT 100)
RETURNS TABLE (
    id UUID,
    channel VARCHAR(32),
    reference_id VARCHAR(128),
    template_code VARCHAR(64),
    variables JSONB,
    retry_count INTEGER
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        n.id,
        n.channel,
        n.reference_id,
        n.template_code,
        n.variables,
        n.retry_count
    FROM sys_notification_audit n
    WHERE n.status = 'PENDING'
      AND n.retry_count < n.max_retries
      AND (n.next_retry_time IS NULL OR n.next_retry_time <= NOW())
    ORDER BY n.created_at ASC
    LIMIT p_limit;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_pending_retry_notifications(INTEGER) IS '获取待重试的通知列表';