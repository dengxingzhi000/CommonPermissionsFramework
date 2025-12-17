-- ======================================================================
-- 审批服务数据库 (db_approval)
-- 职责：权限申请、审批流程管理
-- ======================================================================

-- 创建数据库
-- CREATE DATABASE db_approval WITH ENCODING = 'UTF8';

-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ======================================================================
-- 1. 权限审批表 (sys_permission_approval)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_permission_approval (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id UUID NOT NULL,                      -- 关联 db_user.sys_user.id
    approval_type SMALLINT NOT NULL
        CONSTRAINT chk_approval_type CHECK (approval_type IN (1, 2, 3)),
    target_user_id UUID,                             -- 关联 db_user.sys_user.id
    role_ids UUID[],                                 -- 关联 db_permission.sys_role.id
    permission_ids UUID[],                           -- 关联 db_permission.sys_permission.id
    effective_time TIMESTAMPTZ,
    expire_time TIMESTAMPTZ,
    apply_reason TEXT,
    business_justification TEXT,
    approval_status SMALLINT NOT NULL DEFAULT 0
        CONSTRAINT chk_approval_status CHECK (approval_status IN (0, 1, 2, 3, 4)),
    current_approver_id UUID,                        -- 关联 db_user.sys_user.id
    approval_chain JSONB,
    approved_by UUID,                                -- 关联 db_user.sys_user.id
    approved_time TIMESTAMPTZ,
    reject_reason TEXT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_pa_applicant ON sys_permission_approval(applicant_id);
CREATE INDEX idx_pa_target ON sys_permission_approval(target_user_id) WHERE target_user_id IS NOT NULL;
CREATE INDEX idx_pa_status ON sys_permission_approval(approval_status);
CREATE INDEX idx_pa_approver ON sys_permission_approval(current_approver_id)
    WHERE current_approver_id IS NOT NULL;
CREATE INDEX idx_pa_expire ON sys_permission_approval(expire_time)
    WHERE approval_status = 2 AND expire_time IS NOT NULL;
CREATE INDEX idx_pa_approval_chain ON sys_permission_approval USING GIN (approval_chain)
    WHERE approval_chain IS NOT NULL;
CREATE INDEX idx_pa_role_ids ON sys_permission_approval USING GIN (role_ids)
    WHERE role_ids IS NOT NULL;
CREATE INDEX idx_pa_permission_ids ON sys_permission_approval USING GIN (permission_ids)
    WHERE permission_ids IS NOT NULL;

COMMENT ON TABLE sys_permission_approval IS '权限申请审批表';
COMMENT ON COLUMN sys_permission_approval.applicant_id IS '申请人ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_permission_approval.target_user_id IS '目标用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_permission_approval.approval_type IS '申请类型:1-角色申请,2-权限申请,3-临时授权';
COMMENT ON COLUMN sys_permission_approval.approval_status IS '审批状态:0-待审批,1-审批中,2-已批准,3-已拒绝,4-已撤回';
COMMENT ON COLUMN sys_permission_approval.role_ids IS '角色ID数组(跨库关联db_permission.sys_role)';
COMMENT ON COLUMN sys_permission_approval.permission_ids IS '权限ID数组(跨库关联db_permission.sys_permission)';
COMMENT ON COLUMN sys_permission_approval.approval_chain IS '审批链(JSONB格式)';

-- ======================================================================
-- 注意：update_time 字段由 MyBatis-Plus MetaObjectHandler 自动填充
-- 参见：common/data/.../MyMetaObjectHandler.java
-- ======================================================================

-- ======================================================================
-- 审批统计视图
-- ======================================================================
CREATE OR REPLACE VIEW v_approval_statistics AS
SELECT
    approval_status,
    approval_type,
    COUNT(*) as total_count,
    COUNT(CASE WHEN create_time >= DATE_TRUNC('day', NOW()) THEN 1 END) as today_count,
    COUNT(CASE WHEN create_time >= DATE_TRUNC('week', NOW()) THEN 1 END) as week_count,
    COUNT(CASE WHEN create_time >= DATE_TRUNC('month', NOW()) THEN 1 END) as month_count
FROM sys_permission_approval
GROUP BY approval_status, approval_type;

COMMENT ON VIEW v_approval_statistics IS '审批统计视图';

-- ======================================================================
-- 查询待审批列表的函数
-- ======================================================================
CREATE OR REPLACE FUNCTION get_pending_approvals(p_approver_id UUID)
RETURNS TABLE (
    id UUID,
    applicant_id UUID,
    approval_type SMALLINT,
    apply_reason TEXT,
    create_time TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        pa.id,
        pa.applicant_id,
        pa.approval_type,
        pa.apply_reason,
        pa.create_time
    FROM sys_permission_approval pa
    WHERE pa.current_approver_id = p_approver_id
      AND pa.approval_status IN (0, 1)
    ORDER BY pa.create_time ASC;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_pending_approvals(UUID) IS '获取待审批列表';