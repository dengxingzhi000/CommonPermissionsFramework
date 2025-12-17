-- ======================================================================
-- 权限服务数据库 (db_permission)
-- 职责：RBAC权限管理、角色管理、数据权限规则
-- ======================================================================

-- 创建数据库
-- CREATE DATABASE db_permission WITH ENCODING = 'UTF8';

-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ======================================================================
-- 1. 角色表 (sys_role)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(128) NOT NULL,
    role_desc TEXT,
    role_level SMALLINT NOT NULL DEFAULT 1,
    data_scope SMALLINT NOT NULL DEFAULT 5
        CONSTRAINT chk_data_scope CHECK (data_scope IN (1, 2, 3, 4, 5)),
    max_approval_amount DECIMAL(18, 2),
    business_scope JSONB,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_role_status CHECK (status IN (0, 1)),
    sort_order INTEGER NOT NULL DEFAULT 0,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_role_code ON sys_role(role_code) WHERE NOT deleted;
CREATE INDEX idx_role_status ON sys_role(status) WHERE NOT deleted;
CREATE INDEX idx_role_business_scope ON sys_role USING GIN (business_scope) WHERE business_scope IS NOT NULL;

COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.data_scope IS '数据权限:1-全部,2-自定义,3-本部门,4-本部门及以下,5-仅本人';
COMMENT ON COLUMN sys_role.business_scope IS '业务范围(JSONB格式)';

-- ======================================================================
-- 2. 权限表 (sys_permission)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    permission_type SMALLINT NOT NULL DEFAULT 3
        CONSTRAINT chk_permission_type CHECK (permission_type IN (1, 2, 3, 4, 5)),
    route_path VARCHAR(256),
    component VARCHAR(256),
    redirect VARCHAR(256),
    icon VARCHAR(128),
    api_path VARCHAR(256),
    http_method VARCHAR(32),
    permission_level SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_permission_level CHECK (permission_level IN (1, 2, 3, 4)),
    risk_level SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_risk_level CHECK (risk_level IN (1, 2, 3, 4)),
    need_approval BOOLEAN NOT NULL DEFAULT FALSE,
    need_two_factor BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_perm_status CHECK (status IN (0, 1)),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_perm_parent FOREIGN KEY (parent_id)
        REFERENCES sys_permission(id) ON DELETE SET NULL
);

CREATE INDEX idx_perm_parent ON sys_permission(parent_id) WHERE NOT deleted;
CREATE INDEX idx_perm_code ON sys_permission(permission_code) WHERE NOT deleted;
CREATE INDEX idx_perm_type ON sys_permission(permission_type) WHERE NOT deleted;
CREATE INDEX idx_perm_api ON sys_permission(api_path, http_method) WHERE NOT deleted AND api_path IS NOT NULL;

COMMENT ON TABLE sys_permission IS '权限表';
COMMENT ON COLUMN sys_permission.permission_type IS '类型:1-目录,2-菜单,3-按钮,4-API,5-数据';
COMMENT ON COLUMN sys_permission.permission_level IS '权限等级:1-普通,2-敏感,3-机密,4-绝密';
COMMENT ON COLUMN sys_permission.risk_level IS '风险等级:1-低,2-中,3-高,4-极高';

-- ======================================================================
-- 3. 用户角色关联表 (sys_user_role) - 支持临时角色授权
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                           -- 关联 db_user.sys_user.id
    role_id UUID NOT NULL,
    effective_time TIMESTAMPTZ,
    expire_time TIMESTAMPTZ,
    approval_status SMALLINT NOT NULL DEFAULT 2
        CONSTRAINT chk_ur_approval_status CHECK (approval_status IN (0, 1, 2, 3)),
    approved_by UUID,
    approved_time TIMESTAMPTZ,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT chk_time_range CHECK (
        (effective_time IS NULL AND expire_time IS NULL) OR
        (effective_time IS NOT NULL AND expire_time IS NOT NULL AND expire_time > effective_time)
    )
);

CREATE INDEX idx_ur_user ON sys_user_role(user_id);
CREATE INDEX idx_ur_role ON sys_user_role(role_id);
CREATE INDEX idx_ur_active ON sys_user_role(user_id, approval_status)
    WHERE approval_status = 2;
CREATE INDEX idx_ur_expire ON sys_user_role(expire_time)
    WHERE expire_time IS NOT NULL AND approval_status = 2;

COMMENT ON TABLE sys_user_role IS '用户角色关联表(支持临时授权)';
COMMENT ON COLUMN sys_user_role.user_id IS '用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_user_role.approval_status IS '审批状态:0-待审批,1-审批中,2-已批准,3-已拒绝';

-- ======================================================================
-- 4. 角色权限关联表 (sys_role_permission)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT uk_role_perm UNIQUE (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE
);

CREATE INDEX idx_rp_role ON sys_role_permission(role_id);
CREATE INDEX idx_rp_perm ON sys_role_permission(permission_id);

COMMENT ON TABLE sys_role_permission IS '角色权限关联表';

-- ======================================================================
-- 5. 角色部门关联表 (sys_role_dept) - 用于自定义数据权限
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_role_dept (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    dept_id UUID NOT NULL,                           -- 关联 db_org.sys_dept.id
    include_children BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT uk_role_dept UNIQUE (role_id, dept_id),
    CONSTRAINT fk_rd_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE
);

CREATE INDEX idx_rd_role ON sys_role_dept(role_id);
CREATE INDEX idx_rd_dept ON sys_role_dept(dept_id);

COMMENT ON TABLE sys_role_dept IS '角色部门关联表-用于自定义数据权限范围';
COMMENT ON COLUMN sys_role_dept.dept_id IS '部门ID(跨库关联db_org.sys_dept)';
COMMENT ON COLUMN sys_role_dept.include_children IS '是否包含子部门';

-- ======================================================================
-- 6. 数据权限规则表 (sys_data_permission_rule)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_data_permission_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name VARCHAR(100) NOT NULL,
    rule_code VARCHAR(64) NOT NULL UNIQUE,
    resource_type VARCHAR(50) NOT NULL,
    rule_type SMALLINT NOT NULL
        CONSTRAINT chk_rule_type CHECK (rule_type IN (1, 2, 3, 4, 5)),
    rule_config JSONB NOT NULL DEFAULT '{}',
    sql_condition TEXT,
    visible_fields TEXT[],
    editable_fields TEXT[],
    masked_fields TEXT[],
    priority INTEGER NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_rule_status CHECK (status IN (0, 1)),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_dpr_code ON sys_data_permission_rule(rule_code) WHERE NOT deleted;
CREATE INDEX idx_dpr_resource ON sys_data_permission_rule(resource_type) WHERE NOT deleted;
CREATE INDEX idx_dpr_type ON sys_data_permission_rule(rule_type) WHERE NOT deleted;
CREATE INDEX idx_dpr_config ON sys_data_permission_rule USING GIN (rule_config);

COMMENT ON TABLE sys_data_permission_rule IS '数据权限规则表';
COMMENT ON COLUMN sys_data_permission_rule.rule_type IS '规则类型:1-全部,2-自定义SQL,3-本部门,4-本部门及以下,5-仅本人';

-- ======================================================================
-- 7. 角色数据权限规则关联表 (sys_role_data_rule)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_role_data_rule (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    rule_id UUID NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT uk_role_rule UNIQUE (role_id, rule_id),
    CONSTRAINT fk_rdr_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_rdr_rule FOREIGN KEY (rule_id)
        REFERENCES sys_data_permission_rule(id) ON DELETE CASCADE
);

CREATE INDEX idx_rdr_role ON sys_role_data_rule(role_id);
CREATE INDEX idx_rdr_rule ON sys_role_data_rule(rule_id);

COMMENT ON TABLE sys_role_data_rule IS '角色数据权限规则关联表';

-- ======================================================================
-- 8. 临时权限表 (sys_temp_permission)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_temp_permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                           -- 关联 db_user.sys_user.id
    permission_id UUID NOT NULL,
    approval_id UUID,                                -- 关联 db_approval.sys_permission_approval.id
    effective_time TIMESTAMPTZ NOT NULL,
    expire_time TIMESTAMPTZ NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_temp_status CHECK (status IN (0, 1)),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT fk_tp_perm FOREIGN KEY (permission_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE
);

CREATE INDEX idx_tp_user ON sys_temp_permission(user_id);
CREATE INDEX idx_tp_active ON sys_temp_permission(user_id, status, effective_time, expire_time)
    WHERE status = 1;
CREATE INDEX idx_tp_expire ON sys_temp_permission(expire_time) WHERE status = 1;

COMMENT ON TABLE sys_temp_permission IS '临时权限表-用于临时授权';
COMMENT ON COLUMN sys_temp_permission.user_id IS '用户ID(跨库关联db_user.sys_user)';
COMMENT ON COLUMN sys_temp_permission.approval_id IS '审批ID(跨库关联db_approval.sys_permission_approval)';

-- ======================================================================
-- 注意：update_time 字段由 MyBatis-Plus MetaObjectHandler 自动填充
-- 参见：common/data/.../MyMetaObjectHandler.java
-- ======================================================================

-- ======================================================================
-- 查询用户有效权限的视图/函数
-- ======================================================================
CREATE OR REPLACE FUNCTION get_user_permissions(p_user_id UUID)
RETURNS TABLE (
    permission_code VARCHAR(128),
    permission_name VARCHAR(128),
    permission_type SMALLINT,
    api_path VARCHAR(256),
    http_method VARCHAR(32)
) AS $$
BEGIN
    RETURN QUERY
    SELECT DISTINCT
        p.permission_code,
        p.permission_name,
        p.permission_type,
        p.api_path,
        p.http_method
    FROM sys_permission p
    INNER JOIN sys_role_permission rp ON p.id = rp.permission_id
    INNER JOIN sys_user_role ur ON rp.role_id = ur.role_id
    WHERE ur.user_id = p_user_id
      AND ur.approval_status = 2
      AND (ur.expire_time IS NULL OR ur.expire_time > NOW())
      AND p.status = 1
      AND NOT p.deleted;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_user_permissions(UUID) IS '获取用户的有效权限列表';