-- ======================================================================
-- 数据冗余方案 - 跨库查询优化
-- ======================================================================
--
-- 设计原则（参考阿里、美团实践）：
-- 1. 读多写少的关联数据做冗余
-- 2. 冗余字段选择：高频查询、变更低频、数据量小
-- 3. 通过事件驱动保持最终一致性
-- ======================================================================

-- ======================================================================
-- db_permission 库 - 添加用户冗余字段
-- ======================================================================

-- sys_user_role 表添加用户冗余信息
ALTER TABLE sys_user_role
ADD COLUMN IF NOT EXISTS username VARCHAR(64),
ADD COLUMN IF NOT EXISTS real_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS user_status SMALLINT DEFAULT 1;

COMMENT ON COLUMN sys_user_role.username IS '冗余字段：用户名（来自db_user.sys_user）';
COMMENT ON COLUMN sys_user_role.real_name IS '冗余字段：真实姓名（来自db_user.sys_user）';
COMMENT ON COLUMN sys_user_role.user_status IS '冗余字段：用户状态（来自db_user.sys_user）';

-- 创建索引支持按用户名查询
CREATE INDEX IF NOT EXISTS idx_ur_username ON sys_user_role(username)
    WHERE username IS NOT NULL;

-- ======================================================================
-- db_org 库 - 添加负责人冗余字段
-- ======================================================================

-- sys_dept 表添加负责人冗余信息
ALTER TABLE sys_dept
ADD COLUMN IF NOT EXISTS leader_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS leader_phone VARCHAR(32);

COMMENT ON COLUMN sys_dept.leader_name IS '冗余字段：负责人姓名（来自db_user.sys_user）';
COMMENT ON COLUMN sys_dept.leader_phone IS '冗余字段：负责人电话（来自db_user.sys_user）';

-- ======================================================================
-- db_approval 库 - 添加用户和角色冗余字段
-- ======================================================================

-- sys_permission_approval 表添加冗余信息
ALTER TABLE sys_permission_approval
ADD COLUMN IF NOT EXISTS applicant_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS applicant_dept_name VARCHAR(128),
ADD COLUMN IF NOT EXISTS target_user_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS approver_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS role_names TEXT[],
ADD COLUMN IF NOT EXISTS permission_names TEXT[];

COMMENT ON COLUMN sys_permission_approval.applicant_name IS '冗余字段：申请人姓名';
COMMENT ON COLUMN sys_permission_approval.applicant_dept_name IS '冗余字段：申请人部门名称';
COMMENT ON COLUMN sys_permission_approval.target_user_name IS '冗余字段：目标用户姓名';
COMMENT ON COLUMN sys_permission_approval.approver_name IS '冗余字段：审批人姓名';
COMMENT ON COLUMN sys_permission_approval.role_names IS '冗余字段：角色名称数组';
COMMENT ON COLUMN sys_permission_approval.permission_names IS '冗余字段：权限名称数组';

CREATE INDEX IF NOT EXISTS idx_pa_applicant_name ON sys_permission_approval(applicant_name)
    WHERE applicant_name IS NOT NULL;

-- ======================================================================
-- db_audit 库 - 审计日志已有冗余字段（username, real_name, dept_id）
-- 只需补充部门名称
-- ======================================================================

ALTER TABLE sys_audit_log
ADD COLUMN IF NOT EXISTS dept_name VARCHAR(128);

COMMENT ON COLUMN sys_audit_log.dept_name IS '冗余字段：部门名称（来自db_org.sys_dept）';

ALTER TABLE sys_sensitive_operation_log
ADD COLUMN IF NOT EXISTS real_name VARCHAR(64),
ADD COLUMN IF NOT EXISTS dept_name VARCHAR(128);

COMMENT ON COLUMN sys_sensitive_operation_log.real_name IS '冗余字段：用户真实姓名';
COMMENT ON COLUMN sys_sensitive_operation_log.dept_name IS '冗余字段：部门名称';

-- ======================================================================
-- db_notify 库 - 添加用户冗余字段
-- ======================================================================

ALTER TABLE sys_notification_audit
ADD COLUMN IF NOT EXISTS real_name VARCHAR(64);

COMMENT ON COLUMN sys_notification_audit.real_name IS '冗余字段：用户真实姓名';

-- ======================================================================
-- 冗余数据初始化脚本（一次性执行）
-- 注意：需要跨库执行，建议通过应用层批量更新
-- ======================================================================

-- 以下为示例，实际执行需通过 CrossDatabaseSyncService
/*
-- 1. 同步 sys_user_role 的用户信息
UPDATE sys_user_role ur SET
    username = u.username,
    real_name = u.real_name,
    user_status = u.status
FROM sys_user u  -- 这是跨库操作，需要通过应用层实现
WHERE ur.user_id = u.id;

-- 2. 同步 sys_dept 的负责人信息
UPDATE sys_dept d SET
    leader_name = u.real_name,
    leader_phone = u.phone
FROM sys_user u
WHERE d.leader_id = u.id;
*/
