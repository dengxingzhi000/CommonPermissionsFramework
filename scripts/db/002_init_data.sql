-- ======================================================================
-- CommonPermissionsFramework 初始数据脚本
-- 数据库：PostgreSQL 15+
-- ======================================================================

-- ======================================================================
-- 1. 初始部门数据
-- ======================================================================
INSERT INTO sys_dept (id, parent_id, dept_code, dept_name, dept_type, sort_order, status) VALUES
    ('00000000-0000-0000-0000-000000000001', NULL, 'ROOT', '总公司', 2, 0, 1),
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'TECH', '技术部', 1, 1, 1),
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'HR', '人力资源部', 2, 2, 1),
    ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'FINANCE', '财务部', 2, 3, 1),
    ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002', 'DEV', '研发组', 1, 1, 1),
    ('00000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000002', 'OPS', '运维组', 3, 2, 1),
    ('00000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000002', 'QA', '测试组', 3, 3, 1)
ON CONFLICT (dept_code) DO NOTHING;

-- ======================================================================
-- 2. 初始角色数据
-- ======================================================================
INSERT INTO sys_role (id, role_code, role_name, role_desc, role_level, data_scope, status, sort_order) VALUES
    -- 超级管理员：最高权限
    ('00000000-0000-0000-0000-000000000101', 'ROLE_SUPER_ADMIN', '超级管理员', '系统最高权限，可管理所有资源', 100, 1, 1, 0),
    -- 系统管理员：管理用户、角色、权限
    ('00000000-0000-0000-0000-000000000102', 'ROLE_ADMIN', '系统管理员', '管理用户、角色、权限配置', 90, 1, 1, 1),
    -- 安全管理员：审计、安全配置
    ('00000000-0000-0000-0000-000000000103', 'ROLE_SECURITY', '安全管理员', '负责安全审计和配置', 80, 1, 1, 2),
    -- 审批员：权限审批
    ('00000000-0000-0000-0000-000000000104', 'ROLE_APPROVER', '审批员', '负责权限申请审批', 70, 4, 1, 3),
    -- 部门经理：部门级数据权限
    ('00000000-0000-0000-0000-000000000105', 'ROLE_MANAGER', '部门经理', '部门管理权限', 60, 4, 1, 4),
    -- 普通用户：基础权限
    ('00000000-0000-0000-0000-000000000106', 'ROLE_USER', '普通用户', '普通用户基础权限', 10, 5, 1, 5),
    -- 访客：只读权限
    ('00000000-0000-0000-0000-000000000107', 'ROLE_GUEST', '访客', '只读访问权限', 1, 5, 1, 6),
    -- 审计员：只读审计日志
    ('00000000-0000-0000-0000-000000000108', 'ROLE_AUDITOR', '审计员', '查看审计日志权限', 50, 1, 1, 7)
ON CONFLICT (role_code) DO NOTHING;

-- ======================================================================
-- 3. 初始权限数据
-- ======================================================================

-- 3.1 系统管理目录
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, icon, sort_order, status) VALUES
    ('00000000-0000-0000-0001-000000000001', NULL, 'system', '系统管理', 1, '/system', 'setting', 1, 1),
    ('00000000-0000-0000-0001-000000000002', NULL, 'monitor', '系统监控', 1, '/monitor', 'monitor', 2, 1),
    ('00000000-0000-0000-0001-000000000003', NULL, 'audit', '审计管理', 1, '/audit', 'audit', 3, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.2 用户管理菜单及按钮
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    -- 用户管理菜单
    ('00000000-0000-0000-0001-000000000011', '00000000-0000-0000-0001-000000000001', 'system:user', '用户管理', 2, '/system/user', 'system/user/index', '/api/system/users', 'GET', 1, 1, 1),
    -- 用户管理按钮
    ('00000000-0000-0000-0001-000000000012', '00000000-0000-0000-0001-000000000011', 'system:user:list', '用户列表', 4, NULL, NULL, '/api/system/users', 'GET', 1, 1, 1),
    ('00000000-0000-0000-0001-000000000013', '00000000-0000-0000-0001-000000000011', 'system:user:detail', '用户详情', 4, NULL, NULL, '/api/system/users/*', 'GET', 1, 2, 1),
    ('00000000-0000-0000-0001-000000000014', '00000000-0000-0000-0001-000000000011', 'system:user:add', '新增用户', 3, NULL, NULL, '/api/system/users', 'POST', 2, 3, 1),
    ('00000000-0000-0000-0001-000000000015', '00000000-0000-0000-0001-000000000011', 'system:user:edit', '编辑用户', 3, NULL, NULL, '/api/system/users/*', 'PUT', 2, 4, 1),
    ('00000000-0000-0000-0001-000000000016', '00000000-0000-0000-0001-000000000011', 'system:user:delete', '删除用户', 3, NULL, NULL, '/api/system/users/*', 'DELETE', 3, 5, 1),
    ('00000000-0000-0000-0001-000000000017', '00000000-0000-0000-0001-000000000011', 'system:user:reset-pwd', '重置密码', 3, NULL, NULL, '/api/system/users/*/password', 'PUT', 3, 6, 1),
    ('00000000-0000-0000-0001-000000000018', '00000000-0000-0000-0001-000000000011', 'system:user:export', '导出用户', 3, NULL, NULL, '/api/system/users/export', 'GET', 2, 7, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.3 角色管理菜单及按钮
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    -- 角色管理菜单
    ('00000000-0000-0000-0001-000000000021', '00000000-0000-0000-0001-000000000001', 'system:role', '角色管理', 2, '/system/role', 'system/role/index', '/api/system/roles', 'GET', 1, 2, 1),
    -- 角色管理按钮
    ('00000000-0000-0000-0001-000000000022', '00000000-0000-0000-0001-000000000021', 'system:role:list', '角色列表', 4, NULL, NULL, '/api/system/roles', 'GET', 1, 1, 1),
    ('00000000-0000-0000-0001-000000000023', '00000000-0000-0000-0001-000000000021', 'system:role:detail', '角色详情', 4, NULL, NULL, '/api/system/roles/*', 'GET', 1, 2, 1),
    ('00000000-0000-0000-0001-000000000024', '00000000-0000-0000-0001-000000000021', 'system:role:add', '新增角色', 3, NULL, NULL, '/api/system/roles', 'POST', 2, 3, 1),
    ('00000000-0000-0000-0001-000000000025', '00000000-0000-0000-0001-000000000021', 'system:role:edit', '编辑角色', 3, NULL, NULL, '/api/system/roles/*', 'PUT', 2, 4, 1),
    ('00000000-0000-0000-0001-000000000026', '00000000-0000-0000-0001-000000000021', 'system:role:delete', '删除角色', 3, NULL, NULL, '/api/system/roles/*', 'DELETE', 3, 5, 1),
    ('00000000-0000-0000-0001-000000000027', '00000000-0000-0000-0001-000000000021', 'system:role:permission', '分配权限', 3, NULL, NULL, '/api/system/roles/*/permissions', 'PUT', 3, 6, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.4 权限管理菜单及按钮
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    -- 权限管理菜单
    ('00000000-0000-0000-0001-000000000031', '00000000-0000-0000-0001-000000000001', 'system:permission', '权限管理', 2, '/system/permission', 'system/permission/index', '/api/system/permissions', 'GET', 1, 3, 1),
    -- 权限管理按钮
    ('00000000-0000-0000-0001-000000000032', '00000000-0000-0000-0001-000000000031', 'system:permission:list', '权限列表', 4, NULL, NULL, '/api/system/permissions', 'GET', 1, 1, 1),
    ('00000000-0000-0000-0001-000000000033', '00000000-0000-0000-0001-000000000031', 'system:permission:tree', '权限树', 4, NULL, NULL, '/api/system/permissions/tree', 'GET', 1, 2, 1),
    ('00000000-0000-0000-0001-000000000034', '00000000-0000-0000-0001-000000000031', 'system:permission:add', '新增权限', 3, NULL, NULL, '/api/system/permissions', 'POST', 3, 3, 1),
    ('00000000-0000-0000-0001-000000000035', '00000000-0000-0000-0001-000000000031', 'system:permission:edit', '编辑权限', 3, NULL, NULL, '/api/system/permissions/*', 'PUT', 3, 4, 1),
    ('00000000-0000-0000-0001-000000000036', '00000000-0000-0000-0001-000000000031', 'system:permission:delete', '删除权限', 3, NULL, NULL, '/api/system/permissions/*', 'DELETE', 4, 5, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.5 部门管理菜单及按钮
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    -- 部门管理菜单
    ('00000000-0000-0000-0001-000000000041', '00000000-0000-0000-0001-000000000001', 'system:dept', '部门管理', 2, '/system/dept', 'system/dept/index', '/api/system/depts', 'GET', 1, 4, 1),
    -- 部门管理按钮
    ('00000000-0000-0000-0001-000000000042', '00000000-0000-0000-0001-000000000041', 'system:dept:list', '部门列表', 4, NULL, NULL, '/api/system/depts', 'GET', 1, 1, 1),
    ('00000000-0000-0000-0001-000000000043', '00000000-0000-0000-0001-000000000041', 'system:dept:tree', '部门树', 4, NULL, NULL, '/api/system/depts/tree', 'GET', 1, 2, 1),
    ('00000000-0000-0000-0001-000000000044', '00000000-0000-0000-0001-000000000041', 'system:dept:add', '新增部门', 3, NULL, NULL, '/api/system/depts', 'POST', 2, 3, 1),
    ('00000000-0000-0000-0001-000000000045', '00000000-0000-0000-0001-000000000041', 'system:dept:edit', '编辑部门', 3, NULL, NULL, '/api/system/depts/*', 'PUT', 2, 4, 1),
    ('00000000-0000-0000-0001-000000000046', '00000000-0000-0000-0001-000000000041', 'system:dept:delete', '删除部门', 3, NULL, NULL, '/api/system/depts/*', 'DELETE', 3, 5, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.6 审计日志菜单及按钮
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    -- 审计日志菜单
    ('00000000-0000-0000-0001-000000000051', '00000000-0000-0000-0001-000000000003', 'audit:log', '操作日志', 2, '/audit/log', 'audit/log/index', '/api/audit/logs', 'GET', 2, 1, 1),
    -- 审计日志按钮
    ('00000000-0000-0000-0001-000000000052', '00000000-0000-0000-0001-000000000051', 'audit:log:list', '日志列表', 4, NULL, NULL, '/api/audit/logs', 'GET', 2, 1, 1),
    ('00000000-0000-0000-0001-000000000053', '00000000-0000-0000-0001-000000000051', 'audit:log:detail', '日志详情', 4, NULL, NULL, '/api/audit/logs/*', 'GET', 2, 2, 1),
    ('00000000-0000-0000-0001-000000000054', '00000000-0000-0000-0001-000000000051', 'audit:log:export', '导出日志', 3, NULL, NULL, '/api/audit/logs/export', 'GET', 3, 3, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.7 权限审批菜单及按钮
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    -- 权限审批菜单
    ('00000000-0000-0000-0001-000000000061', '00000000-0000-0000-0001-000000000001', 'system:approval', '权限审批', 2, '/system/approval', 'system/approval/index', '/api/system/approvals', 'GET', 1, 5, 1),
    -- 权限审批按钮
    ('00000000-0000-0000-0001-000000000062', '00000000-0000-0000-0001-000000000061', 'system:approval:list', '审批列表', 4, NULL, NULL, '/api/system/approvals', 'GET', 1, 1, 1),
    ('00000000-0000-0000-0001-000000000063', '00000000-0000-0000-0001-000000000061', 'system:approval:apply', '申请权限', 3, NULL, NULL, '/api/system/approvals', 'POST', 1, 2, 1),
    ('00000000-0000-0000-0001-000000000064', '00000000-0000-0000-0001-000000000061', 'system:approval:approve', '审批通过', 3, NULL, NULL, '/api/system/approvals/*/approve', 'POST', 2, 3, 1),
    ('00000000-0000-0000-0001-000000000065', '00000000-0000-0000-0001-000000000061', 'system:approval:reject', '审批拒绝', 3, NULL, NULL, '/api/system/approvals/*/reject', 'POST', 2, 4, 1),
    ('00000000-0000-0000-0001-000000000066', '00000000-0000-0000-0001-000000000061', 'system:approval:withdraw', '撤回申请', 3, NULL, NULL, '/api/system/approvals/*/withdraw', 'POST', 1, 5, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- 3.8 监控中心菜单
INSERT INTO sys_permission (id, parent_id, permission_code, permission_name, permission_type, route_path, component, api_path, http_method, permission_level, sort_order, status) VALUES
    ('00000000-0000-0000-0001-000000000071', '00000000-0000-0000-0001-000000000002', 'monitor:online', '在线用户', 2, '/monitor/online', 'monitor/online/index', '/api/monitor/online', 'GET', 2, 1, 1),
    ('00000000-0000-0000-0001-000000000072', '00000000-0000-0000-0001-000000000002', 'monitor:server', '服务监控', 2, '/monitor/server', 'monitor/server/index', '/api/monitor/server', 'GET', 2, 2, 1),
    ('00000000-0000-0000-0001-000000000073', '00000000-0000-0000-0001-000000000002', 'monitor:cache', '缓存监控', 2, '/monitor/cache', 'monitor/cache/index', '/api/monitor/cache', 'GET', 2, 3, 1)
ON CONFLICT (permission_code) DO NOTHING;

-- ======================================================================
-- 4. 初始用户数据
-- ======================================================================
-- 密码为 BCrypt 加密的 "Admin@123"
-- $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau
INSERT INTO sys_user (id, username, password, real_name, email, status, dept_id, user_level, account_type) VALUES
    -- 超级管理员
    ('00000000-0000-0000-0000-000000001001', 'superadmin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau', '超级管理员', 'superadmin@example.com', 1, '00000000-0000-0000-0000-000000000001', 3, 3),
    -- 系统管理员
    ('00000000-0000-0000-0000-000000001002', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau', '系统管理员', 'admin@example.com', 1, '00000000-0000-0000-0000-000000000001', 3, 3),
    -- 安全管理员
    ('00000000-0000-0000-0000-000000001003', 'security', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau', '安全管理员', 'security@example.com', 1, '00000000-0000-0000-0000-000000000001', 2, 1),
    -- 部门经理
    ('00000000-0000-0000-0000-000000001004', 'manager', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau', '技术经理', 'manager@example.com', 1, '00000000-0000-0000-0000-000000000002', 2, 1),
    -- 普通用户
    ('00000000-0000-0000-0000-000000001005', 'user', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau', '普通用户', 'user@example.com', 1, '00000000-0000-0000-0000-000000000005', 1, 1),
    -- 测试用户
    ('00000000-0000-0000-0000-000000001006', 'test', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKEQFhOQb.4rVJY3/o/8Rn/0Akau', '测试用户', 'test@example.com', 1, '00000000-0000-0000-0000-000000000007', 1, 1)
ON CONFLICT (username) DO NOTHING;

-- 设置部门负责人
UPDATE sys_dept SET leader_id = '00000000-0000-0000-0000-000000001001' WHERE dept_code = 'ROOT';
UPDATE sys_dept SET leader_id = '00000000-0000-0000-0000-000000001004' WHERE dept_code = 'TECH';

-- ======================================================================
-- 5. 用户角色关联
-- ======================================================================
INSERT INTO sys_user_role (user_id, role_id) VALUES
    -- superadmin -> ROLE_SUPER_ADMIN
    ('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000101'),
    -- admin -> ROLE_ADMIN
    ('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000102'),
    -- security -> ROLE_SECURITY, ROLE_AUDITOR
    ('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000103'),
    ('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000108'),
    -- manager -> ROLE_MANAGER, ROLE_APPROVER
    ('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000105'),
    ('00000000-0000-0000-0000-000000001004', '00000000-0000-0000-0000-000000000104'),
    -- user -> ROLE_USER
    ('00000000-0000-0000-0000-000000001005', '00000000-0000-0000-0000-000000000106'),
    -- test -> ROLE_USER
    ('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000106')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ======================================================================
-- 6. 角色权限关联
-- ======================================================================

-- 6.1 超级管理员：所有权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000101', id FROM sys_permission WHERE NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 6.2 系统管理员：系统管理相关权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000102', id FROM sys_permission
WHERE permission_code LIKE 'system:%' AND NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 6.3 安全管理员：审计相关权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000103', id FROM sys_permission
WHERE (permission_code LIKE 'audit:%' OR permission_code LIKE 'monitor:%') AND NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 6.4 审批员：审批相关权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000104', id FROM sys_permission
WHERE permission_code LIKE 'system:approval%' AND NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 6.5 部门经理：用户查看、部门管理、审批
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000105', id FROM sys_permission
WHERE permission_code IN (
    'system', 'system:user', 'system:user:list', 'system:user:detail',
    'system:dept', 'system:dept:list', 'system:dept:tree',
    'system:approval', 'system:approval:list', 'system:approval:apply'
) AND NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 6.6 普通用户：基础查看权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000106', id FROM sys_permission
WHERE permission_code IN (
    'system', 'system:user', 'system:user:detail',
    'system:dept', 'system:dept:tree',
    'system:approval', 'system:approval:list', 'system:approval:apply', 'system:approval:withdraw'
) AND NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- 6.7 审计员：审计日志只读权限
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000108', id FROM sys_permission
WHERE permission_code IN ('audit', 'audit:log', 'audit:log:list', 'audit:log:detail') AND NOT deleted
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- ======================================================================
-- 7. 数据权限规则（自定义数据权限示例）
-- ======================================================================
-- 部门经理可以查看技术部所有子部门数据
INSERT INTO sys_role_data_permission (role_id, dept_id)
SELECT '00000000-0000-0000-0000-000000000105', id FROM sys_dept
WHERE dept_code IN ('TECH', 'DEV', 'OPS', 'QA')
ON CONFLICT (role_id, dept_id) DO NOTHING;

-- ======================================================================
-- 完成
-- ======================================================================
