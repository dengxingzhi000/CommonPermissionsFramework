-- ======================================================================
-- CommonPermissionsFramework 数据库初始化脚本
-- 数据库：PostgreSQL 15+
-- 使用 PostgreSQL 原生特性：UUID, BOOLEAN, JSONB, TIMESTAMPTZ, ARRAY
-- ======================================================================

-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ======================================================================
-- 1. 部门表 (sys_dept)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_dept (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    dept_code VARCHAR(64) NOT NULL UNIQUE,
    dept_name VARCHAR(128) NOT NULL,
    dept_type SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_dept_type CHECK (dept_type IN (1, 2, 3)),
    leader_id UUID,
    phone VARCHAR(32),
    email VARCHAR(128),
    isolation_level SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_isolation_level CHECK (isolation_level IN (1, 2, 3)),
    sort_order INTEGER NOT NULL DEFAULT 0,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_dept_status CHECK (status IN (0, 1)),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id)
        REFERENCES sys_dept(id) ON DELETE SET NULL
);

CREATE INDEX idx_dept_parent ON sys_dept(parent_id) WHERE NOT deleted;
CREATE INDEX idx_dept_code ON sys_dept(dept_code) WHERE NOT deleted;
CREATE INDEX idx_dept_status ON sys_dept(status) WHERE NOT deleted;

COMMENT ON TABLE sys_dept IS '部门表';
COMMENT ON COLUMN sys_dept.dept_type IS '部门类型:1-业务部门,2-管理部门,3-支持部门';
COMMENT ON COLUMN sys_dept.isolation_level IS '数据隔离级别:1-普通,2-加密,3-完全隔离';
COMMENT ON COLUMN sys_dept.status IS '状态:0-禁用,1-启用';

-- ======================================================================
-- 2. 用户表 (sys_user)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(256) NOT NULL,
    real_name VARCHAR(64),
    id_card VARCHAR(256),
    email VARCHAR(128),
    phone VARCHAR(32),
    avatar VARCHAR(512),
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_user_status CHECK (status IN (0, 1, 2)),
    dept_id UUID,
    user_level SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_user_level CHECK (user_level IN (1, 2, 3)),
    account_type SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_account_type CHECK (account_type IN (1, 2, 3)),
    login_attempts SMALLINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    password_expire_time TIMESTAMPTZ,
    force_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    two_factor_secret VARCHAR(256),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_by UUID,
    last_login_time TIMESTAMPTZ,
    last_login_ip INET,
    last_password_change_time TIMESTAMPTZ,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    remark TEXT,

    CONSTRAINT fk_user_dept FOREIGN KEY (dept_id)
        REFERENCES sys_dept(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_username ON sys_user(username) WHERE NOT deleted;
CREATE INDEX idx_user_dept ON sys_user(dept_id) WHERE NOT deleted;
CREATE INDEX idx_user_status ON sys_user(status) WHERE NOT deleted;
CREATE INDEX idx_user_email ON sys_user(email) WHERE NOT deleted AND email IS NOT NULL;
CREATE INDEX idx_user_phone ON sys_user(phone) WHERE NOT deleted AND phone IS NOT NULL;

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.status IS '状态:0-禁用,1-启用,2-锁定';
COMMENT ON COLUMN sys_user.user_level IS '用户级别:1-普通,2-高级,3-VIP';
COMMENT ON COLUMN sys_user.account_type IS '账户类型:1-内部员工,2-外部审计,3-系统管理员';
COMMENT ON COLUMN sys_user.last_login_ip IS '最后登录IP(使用PostgreSQL INET类型)';

-- ======================================================================
-- 3. 角色表 (sys_role)
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
COMMENT ON COLUMN sys_role.business_scope IS '业务范围(JSONB格式,支持GIN索引查询)';

-- ======================================================================
-- 4. 权限表 (sys_permission)
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
-- 5. 用户角色关联表 (sys_user_role) - 支持临时角色授权
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
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
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE,
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
COMMENT ON COLUMN sys_user_role.effective_time IS '生效时间(临时授权)';
COMMENT ON COLUMN sys_user_role.expire_time IS '过期时间(临时授权)';
COMMENT ON COLUMN sys_user_role.approval_status IS '审批状态:0-待审批,1-审批中,2-已批准,3-已拒绝';

-- ======================================================================
-- 6. 角色权限关联表 (sys_role_permission)
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
-- 7. 数据权限规则表 (sys_role_data_permission) - 用于自定义数据权限
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_role_data_permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    dept_id UUID NOT NULL,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT uk_role_dept UNIQUE (role_id, dept_id),
    CONSTRAINT fk_rdp_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_rdp_dept FOREIGN KEY (dept_id)
        REFERENCES sys_dept(id) ON DELETE CASCADE
);

CREATE INDEX idx_rdp_role ON sys_role_data_permission(role_id);
CREATE INDEX idx_rdp_dept ON sys_role_data_permission(dept_id);

COMMENT ON TABLE sys_role_data_permission IS '角色数据权限规则表-用于自定义数据权限范围';
COMMENT ON COLUMN sys_role_data_permission.dept_id IS '可访问的部门ID';

-- ======================================================================
-- 8. 权限审批表 (sys_permission_approval)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_permission_approval (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_id UUID NOT NULL,
    approval_type SMALLINT NOT NULL
        CONSTRAINT chk_approval_type CHECK (approval_type IN (1, 2, 3)),
    target_user_id UUID,
    role_ids UUID[],
    permission_ids UUID[],
    effective_time TIMESTAMPTZ,
    expire_time TIMESTAMPTZ,
    apply_reason TEXT,
    business_justification TEXT,
    approval_status SMALLINT NOT NULL DEFAULT 0
        CONSTRAINT chk_approval_status CHECK (approval_status IN (0, 1, 2, 3, 4)),
    current_approver_id UUID,
    approval_chain JSONB,
    approved_by UUID,
    approved_time TIMESTAMPTZ,
    reject_reason TEXT,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_pa_applicant FOREIGN KEY (applicant_id)
        REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_pa_applicant ON sys_permission_approval(applicant_id);
CREATE INDEX idx_pa_status ON sys_permission_approval(approval_status);
CREATE INDEX idx_pa_approver ON sys_permission_approval(current_approver_id)
    WHERE current_approver_id IS NOT NULL;
CREATE INDEX idx_pa_expire ON sys_permission_approval(expire_time)
    WHERE approval_status = 2 AND expire_time IS NOT NULL;
CREATE INDEX idx_pa_approval_chain ON sys_permission_approval USING GIN (approval_chain)
    WHERE approval_chain IS NOT NULL;

COMMENT ON TABLE sys_permission_approval IS '权限申请审批表';
COMMENT ON COLUMN sys_permission_approval.approval_type IS '申请类型:1-角色申请,2-权限申请,3-临时授权';
COMMENT ON COLUMN sys_permission_approval.approval_status IS '审批状态:0-待审批,1-审批中,2-已批准,3-已拒绝,4-已撤回';
COMMENT ON COLUMN sys_permission_approval.role_ids IS '角色ID数组(PostgreSQL原生UUID数组)';
COMMENT ON COLUMN sys_permission_approval.permission_ids IS '权限ID数组(PostgreSQL原生UUID数组)';
COMMENT ON COLUMN sys_permission_approval.approval_chain IS '审批链(JSONB格式)';

-- ======================================================================
-- 9. 审计日志表 (sys_audit_log) - 使用分区表提升性能
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID,
    username VARCHAR(64),
    real_name VARCHAR(64),
    dept_id UUID,
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

-- 创建初始分区（按月分区）
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

CREATE INDEX idx_log_user ON sys_audit_log(user_id);
CREATE INDEX idx_log_time ON sys_audit_log(create_time DESC);
CREATE INDEX idx_log_type ON sys_audit_log(operation_type);
CREATE INDEX idx_log_business ON sys_audit_log(business_type, business_id)
    WHERE business_type IS NOT NULL;
CREATE INDEX idx_log_risk ON sys_audit_log(risk_level) WHERE risk_level >= 3;
CREATE INDEX idx_log_request_params ON sys_audit_log USING GIN (request_params)
    WHERE request_params IS NOT NULL;

COMMENT ON TABLE sys_audit_log IS '操作审计日志表(按月分区)';
COMMENT ON COLUMN sys_audit_log.operation_type IS '操作类型:LOGIN,LOGOUT,ADD,UPDATE,DELETE,QUERY,EXPORT,APPROVE';
COMMENT ON COLUMN sys_audit_log.risk_level IS '风险等级:1-低,2-中,3-高,4-极高';
COMMENT ON COLUMN sys_audit_log.status IS '状态:0-失败,1-成功';
COMMENT ON COLUMN sys_audit_log.ip_address IS 'IP地址(使用PostgreSQL INET类型)';
COMMENT ON COLUMN sys_audit_log.request_params IS '请求参数(JSONB格式,支持GIN索引)';

-- ======================================================================
-- 10. 通知审计表 (sys_notification_audit)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_notification_audit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id VARCHAR(128),
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subject VARCHAR(256),
    username VARCHAR(64),
    email VARCHAR(128),
    template_code VARCHAR(64),
    content TEXT,
    variables JSONB,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notify_ref ON sys_notification_audit(reference_id)
    WHERE reference_id IS NOT NULL;
CREATE INDEX idx_notify_channel ON sys_notification_audit(channel);
CREATE INDEX idx_notify_status ON sys_notification_audit(status);
CREATE INDEX idx_notify_time ON sys_notification_audit(created_at DESC);
CREATE INDEX idx_notify_variables ON sys_notification_audit USING GIN (variables)
    WHERE variables IS NOT NULL;

COMMENT ON TABLE sys_notification_audit IS '通知发送审计表';
COMMENT ON COLUMN sys_notification_audit.channel IS '通知渠道:EMAIL,SMS,WECHAT,DINGTALK';
COMMENT ON COLUMN sys_notification_audit.status IS '发送状态:PENDING,SENT,FAILED';
COMMENT ON COLUMN sys_notification_audit.variables IS '模板变量(JSONB格式)';

-- ======================================================================
-- 11. WebAuthn凭证表 (webauthn_credential)
-- ======================================================================
CREATE TABLE IF NOT EXISTS webauthn_credential (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    credential_id VARCHAR(512) NOT NULL UNIQUE
        CONSTRAINT chk_credential_id_not_empty CHECK (credential_id <> ''),
    user_id UUID NOT NULL,
    public_key_pem TEXT NOT NULL
        CONSTRAINT chk_public_key_not_empty CHECK (public_key_pem <> ''),
    alg VARCHAR(64) NOT NULL
        CONSTRAINT chk_alg_valid CHECK (alg IN ('RS256', 'RS384', 'RS512', 'ES256', 'ES384', 'ES512', 'EdDSA')),
    sign_count BIGINT NOT NULL DEFAULT 0
        CONSTRAINT chk_sign_count_positive CHECK (sign_count >= 0),
    device_name VARCHAR(128),
    aaguid UUID,
    transports TEXT[],
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at TIMESTAMPTZ,
    created_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_webauthn_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_webauthn_user_active ON webauthn_credential(user_id, is_active);
CREATE INDEX idx_webauthn_last_used ON webauthn_credential(last_used_at DESC NULLS LAST)
    WHERE is_active = TRUE;
CREATE INDEX idx_webauthn_transports ON webauthn_credential USING GIN (transports)
    WHERE transports IS NOT NULL;

COMMENT ON TABLE webauthn_credential IS 'WebAuthn凭证表';
COMMENT ON COLUMN webauthn_credential.transports IS '支持的传输方式数组(PostgreSQL原生TEXT数组)';
COMMENT ON COLUMN webauthn_credential.aaguid IS 'Authenticator GUID(使用PostgreSQL原生UUID类型)';

-- ======================================================================
-- 12. 临时权限表 (sys_temp_permission)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_temp_permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    approval_id UUID,
    effective_time TIMESTAMPTZ NOT NULL,
    expire_time TIMESTAMPTZ NOT NULL,
    status SMALLINT NOT NULL DEFAULT 1
        CONSTRAINT chk_temp_status CHECK (status IN (0, 1)),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT fk_tp_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_perm FOREIGN KEY (permission_id)
        REFERENCES sys_permission(id) ON DELETE CASCADE,
    CONSTRAINT fk_tp_approval FOREIGN KEY (approval_id)
        REFERENCES sys_permission_approval(id) ON DELETE SET NULL
);

CREATE INDEX idx_tp_user ON sys_temp_permission(user_id);
CREATE INDEX idx_tp_active ON sys_temp_permission(user_id, status, effective_time, expire_time)
    WHERE status = 1;
CREATE INDEX idx_tp_expire ON sys_temp_permission(expire_time) WHERE status = 1;

COMMENT ON TABLE sys_temp_permission IS '临时权限表-用于临时授权';
COMMENT ON COLUMN sys_temp_permission.status IS '状态:0-已失效,1-生效中';

-- ======================================================================
-- 13. 自动更新 update_time 触发器
-- ======================================================================
CREATE OR REPLACE FUNCTION update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.update_time = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为需要的表添加触发器
CREATE TRIGGER trg_dept_update BEFORE UPDATE ON sys_dept
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_user_update BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_role_update BEFORE UPDATE ON sys_role
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_permission_update BEFORE UPDATE ON sys_permission
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_approval_update BEFORE UPDATE ON sys_permission_approval
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

CREATE TRIGGER trg_webauthn_update BEFORE UPDATE ON webauthn_credential
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

-- ======================================================================
-- 14. 审计日志自动分区函数
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
-- 15. OAuth绑定表 (sys_user_oauth)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_user_oauth (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL
        CONSTRAINT chk_oauth_provider CHECK (provider IN ('google', 'github', 'apple', 'wechat', 'dingtalk', 'feishu')),
    oauth_openid VARCHAR(255) NOT NULL,
    oauth_union_id VARCHAR(255),
    oauth_email VARCHAR(128),
    oauth_nickname VARCHAR(64),
    oauth_avatar VARCHAR(512),
    access_token TEXT,
    refresh_token TEXT,
    token_expire_time TIMESTAMPTZ,
    raw_user_info JSONB,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    update_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_time TIMESTAMPTZ,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uk_provider_openid UNIQUE (provider, oauth_openid),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE
);

CREATE INDEX idx_oauth_user ON sys_user_oauth(user_id) WHERE NOT deleted;
CREATE INDEX idx_oauth_provider ON sys_user_oauth(provider) WHERE NOT deleted;
CREATE INDEX idx_oauth_email ON sys_user_oauth(oauth_email) WHERE NOT deleted AND oauth_email IS NOT NULL;
CREATE INDEX idx_oauth_union ON sys_user_oauth(provider, oauth_union_id)
    WHERE NOT deleted AND oauth_union_id IS NOT NULL;

-- 自动更新触发器
CREATE TRIGGER trg_oauth_update BEFORE UPDATE ON sys_user_oauth
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

COMMENT ON TABLE sys_user_oauth IS 'OAuth第三方登录绑定表';
COMMENT ON COLUMN sys_user_oauth.provider IS 'OAuth提供商:google,github,apple,wechat,dingtalk,feishu';
COMMENT ON COLUMN sys_user_oauth.oauth_openid IS 'OAuth开放ID(唯一标识)';
COMMENT ON COLUMN sys_user_oauth.oauth_union_id IS 'OAuth联合ID(用于同一平台多应用)';
COMMENT ON COLUMN sys_user_oauth.raw_user_info IS 'OAuth返回的原始用户信息(JSONB格式)';

-- ======================================================================
-- 16. 角色部门关联表 (sys_role_dept) - 用于自定义数据权限
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_role_dept (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL,
    dept_id UUID NOT NULL,
    include_children BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    create_by UUID,

    CONSTRAINT uk_role_dept UNIQUE (role_id, dept_id),
    CONSTRAINT fk_rd_role FOREIGN KEY (role_id)
        REFERENCES sys_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_rd_dept FOREIGN KEY (dept_id)
        REFERENCES sys_dept(id) ON DELETE CASCADE
);

CREATE INDEX idx_rd_role ON sys_role_dept(role_id);
CREATE INDEX idx_rd_dept ON sys_role_dept(dept_id);

COMMENT ON TABLE sys_role_dept IS '角色部门关联表-用于自定义数据权限范围';
COMMENT ON COLUMN sys_role_dept.include_children IS '是否包含子部门';

-- ======================================================================
-- 17. 数据权限规则表 (sys_data_permission_rule)
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

-- 自动更新触发器
CREATE TRIGGER trg_dpr_update BEFORE UPDATE ON sys_data_permission_rule
    FOR EACH ROW EXECUTE FUNCTION update_timestamp();

COMMENT ON TABLE sys_data_permission_rule IS '数据权限规则表';
COMMENT ON COLUMN sys_data_permission_rule.rule_type IS '规则类型:1-全部,2-自定义SQL,3-本部门,4-本部门及以下,5-仅本人';
COMMENT ON COLUMN sys_data_permission_rule.rule_config IS '规则配置(JSONB格式)';
COMMENT ON COLUMN sys_data_permission_rule.sql_condition IS 'SQL条件表达式';
COMMENT ON COLUMN sys_data_permission_rule.visible_fields IS '可见字段列表(PostgreSQL TEXT数组)';
COMMENT ON COLUMN sys_data_permission_rule.editable_fields IS '可编辑字段列表(PostgreSQL TEXT数组)';
COMMENT ON COLUMN sys_data_permission_rule.masked_fields IS '需脱敏字段列表(PostgreSQL TEXT数组)';

-- ======================================================================
-- 18. 角色数据权限规则关联表 (sys_role_data_rule)
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
-- 19. 敏感操作日志表 (sys_sensitive_operation_log)
-- ======================================================================
CREATE TABLE IF NOT EXISTS sys_sensitive_operation_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
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
    approval_id UUID,
    risk_score SMALLINT DEFAULT 1
        CONSTRAINT chk_risk_score CHECK (risk_score BETWEEN 1 AND 10),
    ip_address INET,
    user_agent TEXT,
    device_fingerprint VARCHAR(128),
    location VARCHAR(128),
    create_time TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_sol_user FOREIGN KEY (user_id)
        REFERENCES sys_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_sol_approval FOREIGN KEY (approval_id)
        REFERENCES sys_permission_approval(id) ON DELETE SET NULL
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
COMMENT ON COLUMN sys_sensitive_operation_log.operation_type IS '操作类型:EXPORT,BULK_UPDATE,BULK_DELETE,DATA_DOWNLOAD,PERMISSION_CHANGE';
COMMENT ON COLUMN sys_sensitive_operation_log.sensitive_data_type IS '敏感数据类型:PERSONAL_INFO,FINANCIAL,MEDICAL,SECRET';
COMMENT ON COLUMN sys_sensitive_operation_log.data_fingerprint IS '数据指纹(SHA256,不存储原始数据)';
COMMENT ON COLUMN sys_sensitive_operation_log.target_ids IS '影响的记录ID列表(PostgreSQL UUID数组)';
COMMENT ON COLUMN sys_sensitive_operation_log.risk_score IS '风险评分:1-10';

-- ======================================================================
-- 完成
-- ======================================================================
