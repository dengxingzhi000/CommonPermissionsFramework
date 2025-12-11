CREATE TABLE sys_user (
                          id UUID PRIMARY KEY,                                 -- 用户ID
                          username VARCHAR(64) NOT NULL UNIQUE,                -- 用户名
                          password VARCHAR(255) NOT NULL,                      -- 密码(BCrypt加密)
                          real_first_name VARCHAR(64) NOT NULL,                -- 真实姓
                          real_last_name VARCHAR(64) NOT NULL,                 -- 真实名
                          id_card CHAR(18),                                    -- 身份证号(加密存储)
                          email VARCHAR(128),                                  -- 邮箱
                          phone VARCHAR(32),                                   -- 手机号
                          avatar VARCHAR(255),                                 -- 头像URL
                          status SMALLINT DEFAULT 1,                           -- 状态:0-禁用,1-启用,2-锁定
                          dept_id UUID,                                        -- 部门ID
                          user_level SMALLINT DEFAULT 1,                       -- 用户级别:1-普通,2-高级,3-VIP
                          account_type SMALLINT DEFAULT 1,                     -- 账户类型:1-内部员工,2-外部审计,3-系统管理员
                          login_attempts INT DEFAULT 0,                        -- 连续登录失败次数
                          locked_until TIMESTAMP,                              -- 锁定截止时间
                          password_expire_time TIMESTAMP,                      -- 密码过期时间
                          force_change_password bool DEFAULT false,                -- 是否强制修改密码
                          two_factor_enabled bool DEFAULT false,                   -- 是否启用双因素认证
                          two_factor_secret VARCHAR(128),                      -- 双因素认证密钥
                          create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,     -- 创建时间
                          create_by uuid,                                      -- 创建人ID
                          update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,     -- 更新时间
                          update_by uuid,                                      -- 更新人ID
                          last_login_time TIMESTAMP,                           -- 最后登录时间
                          last_login_ip inet,                                  -- 最后登录IP
                          last_password_change_time TIMESTAMP,                 -- 最后修改密码时间
                          deleted bool DEFAULT false,                              -- 逻辑删除
                          remark text,                                         -- 备注
                          CONSTRAINT idx_username UNIQUE (username)
);

-- 🔹 索引定义
CREATE INDEX idx_dept_id ON sys_user (dept_id);
CREATE INDEX idx_status ON sys_user (status);
CREATE INDEX idx_account_type ON sys_user (account_type);

COMMENT ON TABLE sys_user IS '用户表';
COMMENT ON COLUMN sys_user.id IS '用户ID';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码(BCrypt加密)';
COMMENT ON COLUMN sys_user.status IS '状态:0-禁用,1-启用,2-锁定';
COMMENT ON COLUMN sys_user.dept_id IS '部门ID';
COMMENT ON COLUMN sys_user.account_type IS '账户类型:1-内部员工,2-外部审计,3-系统管理员';
COMMENT ON COLUMN sys_user.real_first_name IS '真实姓';
COMMENT ON COLUMN sys_user.real_last_name IS '真实名';
COMMENT ON COLUMN sys_user.id_card IS '身份证号(加密存储)';
COMMENT ON COLUMN sys_user.email IS '邮箱';
COMMENT ON COLUMN sys_user.phone IS '手机号';
COMMENT ON COLUMN sys_user.avatar IS '头像URL';
COMMENT ON COLUMN sys_user.user_level IS '用户级别:1-普通,2-高级,3-VIP';
COMMENT ON COLUMN sys_user.login_attempts IS '连续登录失败次数';
COMMENT ON COLUMN sys_user.locked_until IS '锁定截止时间';
COMMENT ON COLUMN sys_user.password_expire_time IS '密码过期时间';
COMMENT ON COLUMN sys_user.force_change_password IS '是否强制修改密码';
COMMENT ON COLUMN sys_user.two_factor_enabled IS '是否启用双因素认证';
COMMENT ON COLUMN sys_user.two_factor_secret IS '双因素认证密钥';
COMMENT ON COLUMN sys_user.create_time IS '创建时间';
COMMENT ON COLUMN sys_user.create_by IS '创建人ID';
COMMENT ON COLUMN sys_user.update_time IS '更新时间';
COMMENT ON COLUMN sys_user.update_by IS '更新人ID';
COMMENT ON COLUMN sys_user.last_login_time IS '最后登录时间';
COMMENT ON COLUMN sys_user.last_login_ip IS '最后登录IP';
COMMENT ON COLUMN sys_user.last_password_change_time IS '最后修改密码时间';
COMMENT ON COLUMN sys_user.deleted IS '逻辑删除';
COMMENT ON COLUMN sys_user.remark IS '备注';

