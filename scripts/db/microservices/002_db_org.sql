-- ======================================================================
-- 组织服务数据库 (db_org)
-- 职责：组织架构管理、部门层级
-- ======================================================================

-- 创建数据库
-- CREATE DATABASE db_org WITH ENCODING = 'UTF8';

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
    leader_id UUID,                                  -- 关联 db_user.sys_user.id
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
CREATE INDEX idx_dept_leader ON sys_dept(leader_id) WHERE NOT deleted AND leader_id IS NOT NULL;

COMMENT ON TABLE sys_dept IS '部门表';
COMMENT ON COLUMN sys_dept.dept_type IS '部门类型:1-业务部门,2-管理部门,3-支持部门';
COMMENT ON COLUMN sys_dept.isolation_level IS '数据隔离级别:1-普通,2-加密,3-完全隔离';
COMMENT ON COLUMN sys_dept.status IS '状态:0-禁用,1-启用';
COMMENT ON COLUMN sys_dept.leader_id IS '部门负责人ID(跨库关联db_user.sys_user)';

-- ======================================================================
-- 注意：update_time 字段由 MyBatis-Plus MetaObjectHandler 自动填充
-- 参见：common/data/.../MyMetaObjectHandler.java
-- ======================================================================

-- ======================================================================
-- 递归查询部门树的函数
-- ======================================================================
CREATE OR REPLACE FUNCTION get_dept_tree(root_id UUID DEFAULT NULL)
RETURNS TABLE (
    id UUID,
    parent_id UUID,
    dept_code VARCHAR(64),
    dept_name VARCHAR(128),
    dept_level INTEGER,
    dept_path TEXT
) AS $$
BEGIN
    RETURN QUERY
    WITH RECURSIVE dept_tree AS (
        SELECT
            d.id,
            d.parent_id,
            d.dept_code,
            d.dept_name,
            1 AS dept_level,
            d.dept_name::TEXT AS dept_path
        FROM sys_dept d
        WHERE (root_id IS NULL AND d.parent_id IS NULL)
           OR (root_id IS NOT NULL AND d.id = root_id)
        AND NOT d.deleted

        UNION ALL

        SELECT
            d.id,
            d.parent_id,
            d.dept_code,
            d.dept_name,
            dt.dept_level + 1,
            dt.dept_path || ' > ' || d.dept_name
        FROM sys_dept d
        INNER JOIN dept_tree dt ON d.parent_id = dt.id
        WHERE NOT d.deleted
    )
    SELECT * FROM dept_tree ORDER BY dept_path;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION get_dept_tree(UUID) IS '递归获取部门树结构';