-- ======================================================================
-- 用户服务数据库 (db_user)
-- 职责：用户账户管理、身份认证、OAuth绑定、WebAuthn
-- ======================================================================

-- 创建数据库
-- CREATE DATABASE db_user WITH ENCODING = 'UTF8';

-- 启用必要扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ======================================================================
-- 1. 用户表 (sys_user)
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
    dept_id UUID,                                    -- 关联 db_org.sys_dept.id
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
    remark TEXT
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
COMMENT ON COLUMN sys_user.dept_id IS '部门ID(跨库关联db_org.sys_dept)';

-- ======================================================================
-- 2. OAuth绑定表 (sys_user_oauth)
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

COMMENT ON TABLE sys_user_oauth IS 'OAuth第三方登录绑定表';
COMMENT ON COLUMN sys_user_oauth.provider IS 'OAuth提供商:google,github,apple,wechat,dingtalk,feishu';

-- ======================================================================
-- 3. WebAuthn凭证表 (webauthn_credential)
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
COMMENT ON COLUMN webauthn_credential.transports IS '支持的传输方式数组';

-- ======================================================================
-- 注意：update_time 字段由 MyBatis-Plus MetaObjectHandler 自动填充
-- 参见：common/data/.../MyMetaObjectHandler.java
-- 不再使用数据库触发器，以保持一致性并减少维护成本
-- ======================================================================