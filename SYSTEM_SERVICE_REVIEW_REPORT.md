# System Service 架构审查报告

**审查日期：** 2025-12-18
**审查范围：** system/service/src/main/java/com/frog/system/service/Impl/*

---

## 📊 执行摘要

| 评估项 | 状态 | 评分 |
|--------|------|------|
| 设计思想 | 跨库统一查询（应用层聚合） | ✅ 明确 |
| 职责划分 | 混乱且不一致 | ⚠️ 需改进 |
| 读写分离应用 | 已配置但完全未使用 | ❌ 未生效 |
| 数据同步应用 | 正确应用 | ✅ 良好 |

---

## 一、设计模式分析

### 1.1 设计思想判定：✅ 跨库统一查询（应用层聚合）

**核心证据：**
- 存在专门的 `CrossDatabaseQueryService`（284 行代码）
- 注释明确说明："用于处理需要跨多个数据库查询的场景，在应用层进行数据聚合"
- 替代了原有的数据库 JOIN 跨库查询

**设计优势：**
- ✅ 避免跨库 JOIN 的性能问题
- ✅ 数据库分库分表友好
- ✅ 便于独立扩展各库
- ✅ 便于添加缓存优化

**CrossDatabaseQueryService 提供的核心方法：**

| 方法 | 跨越的库 | 替代的原 Mapper 方法 |
|------|---------|---------------------|
| `findFirstUserIdByRoleCode` | db_permission → db_user | SysPermissionApprovalMapper.findFirstUserByRoleCode |
| `findUserDeptAndChildren` | db_user → db_org | SysUserMapper.findUserDeptAndChildren |
| `hasAccessToDept` | db_permission → db_user → db_org | SysUserMapper.hasAccessToDept |
| `selectDeptTree` | db_org → db_user | SysDeptMapper.selectDeptTree |
| `findAccessibleDeptIds` | db_permission → db_org | SysRoleDeptMapper.findAccessibleDeptIds |

---

## 二、职责划分问题

### 2.1 问题严重程度分级

#### 🔴 **高严重性问题**

##### **问题 1: SysUserServiceImpl 职责混乱**

**文件：** `system/service/Impl/SysUserServiceImpl.java`

**问题描述：**
```java
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> {
    // ❌ 同时注入了跨库 Mapper 和 CrossDatabaseQueryService
    private final SysUserMapper userMapper;              // 自己库 (db_user)
    private final SysUserRoleMapper userRoleMapper;      // 跨库 (db_permission)
    private final SysPermissionMapper permissionMapper;  // 跨库 (db_permission)
    private final CrossDatabaseQueryService crossDbService;
}
```

**混乱的调用方式：**

| 行号 | 代码 | 问题 |
|-----|------|------|
| Line 101 | `userRoleMapper.findUserRolesWithNames(id)` | 直接跨库查询 |
| Line 143 | `userRoleMapper.findRoleCodesByUserId(userId)` | 直接跨库查询 |
| Line 150 | `permissionMapper.findMenuTreeByUserId(userId)` | 直接跨库查询 |
| Line 189 | `userRoleMapper.batchInsert(...)` | 直接跨库操作 |
| Line 521 | `crossDbService.hasAccessToDept(userId, deptId)` | ✅ 正确使用 |

**影响：**
- 职责不清：既通过 CrossDatabaseQueryService 又直接操作跨库 Mapper
- 维护困难：跨库逻辑散落在多处
- 缓存失效：CrossDatabaseQueryService 可能有缓存，直接调用 Mapper 会绕过
- 事务边界不清：跨库事务难以控制

---

##### **问题 2: SysDeptServiceImpl 直接依赖跨库 Mapper**

**文件：** `system/service/Impl/SysDeptServiceImpl.java`

**问题代码：**
```java
@Service
public class SysDeptServiceImpl {
    private final SysDeptMapper deptMapper;    // db_org
    private final SysUserMapper userMapper;    // ❌ 跨库依赖 db_user

    // Line 56: 直接跨库统计
    Map<UUID, Map<String, Object>> countResult = userMapper.countUsersByDeptIds(deptIds);

    // Line 229: 直接跨库统计
    return userMapper.countUsersByDeptId(deptId);
}
```

**应该改为：**
```java
@Service
public class SysDeptServiceImpl {
    private final SysDeptMapper deptMapper;
    private final CrossDatabaseQueryService crossDbService; // ✅ 统一跨库服务

    // 通过 CrossDatabaseQueryService 查询
    Map<UUID, Integer> userCounts = crossDbService.countUsersByDeptIds(deptIds);
}
```

---

##### **问题 3: SysPermissionApprovalServiceImpl 过多跨库依赖**

**文件：** `system/service/Impl/SysPermissionApprovalServiceImpl.java`

**依赖情况：**
```java
@Service
public class SysPermissionApprovalServiceImpl {
    private final SysPermissionApprovalMapper approvalMapper; // 自己库 (db_approval)
    private final SysUserMapper userMapper;                   // 跨库 (db_user)
    private final SysUserRoleMapper userRoleMapper;           // 跨库 (db_permission)
    private final SysDeptMapper deptMapper;                   // 跨库 (db_org)
    private final CrossDatabaseQueryService crossDbService;   // ✅ 也有这个
}
```

**问题分析：**
- Line 83: 调用 `buildApprovalChain` 需要跨 3 个库查询
- Line 314-360: 直接使用 `deptMapper.getLeaderId(deptId)` 跨库查询
- Line 367-374: 使用 `crossDbService` 查询系统管理员 ✅
- Line 389: 使用 `userMapper.selectById(approverId)` 跨库查询
- Line 202-236: 使用 `userRoleMapper.batchInsert` 跨库操作

**部分已经在使用 CrossDatabaseQueryService（Line 367）：**
```java
// ✅ 正确方式
private UUID getSystemAdmin() {
    return crossDatabaseQueryService.findFirstUserIdByRoleCode(ROLE_ADMIN);
}
```

---

#### 🟢 **良好实践**

##### **SysRoleServiceImpl - 职责清晰**

```java
@Service
public class SysRoleServiceImpl {
    private final SysRoleMapper roleMapper;          // ✅ 只依赖自己库
    private final DataSyncEventPublisher publisher;  // ✅ 发布数据同步事件

    // ✅ 不涉及跨库查询，职责单一
}
```

##### **SysPermissionServiceImpl - 职责清晰**

```java
@Service
public class SysPermissionServiceImpl {
    private final SysPermissionMapper sysPermissionMapper; // ✅ 只依赖自己库

    // ✅ 所有查询都在 db_permission 库内
}
```

---

### 2.2 职责划分原则（推荐）

#### ✅ **推荐的分层架构**

```
┌─────────────────────────────────────────────────────────────┐
│                    Controller Layer                          │
│  (接收请求，参数校验，返回 DTO)                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Service Layer                             │
│  ┌────────────────────┐         ┌────────────────────────┐  │
│  │  SysUserService    │         │  SysRoleService        │  │
│  │  (单库操作)         │         │  (单库操作)             │  │
│  └─────────┬──────────┘         └─────────┬──────────────┘  │
│            │                               │                 │
│            ├───────────────┬───────────────┘                 │
│            ↓               ↓                                 │
│  ┌─────────────────────────────────────────────────┐        │
│  │    CrossDatabaseQueryService                     │        │
│  │    (专门处理跨库聚合查询)                          │        │
│  └──────────────────┬──────────────────────────────┘        │
└────────────────────┼────────────────────────────────────────┘
                     ↓
┌─────────────────────────────────────────────────────────────┐
│                    Mapper Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ UserMapper   │  │ RoleMapper   │  │ DeptMapper   │      │
│  │ (@DS("user"))│  │(@DS("perm")) │  │ (@DS("org")) │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

#### 📋 **职责划分规则**

| 层 | 职责 | 禁止 |
|----|------|------|
| **Service Impl** | 1. 操作自己库的 Mapper<br>2. 调用 CrossDatabaseQueryService<br>3. 发布数据同步事件 | ❌ 注入其他库的 Mapper<br>❌ 直接跨库 JOIN |
| **CrossDatabaseQueryService** | 1. 封装所有跨库查询逻辑<br>2. 应用层数据聚合<br>3. 批量查询优化 | ❌ 包含业务逻辑<br>❌ 操作事务 |
| **Mapper** | 1. 只操作单库<br>2. SQL 查询/更新 | ❌ 跨库 JOIN<br>❌ 业务逻辑 |

---

## 三、读写分离应用情况

### 3.1 配置状态：已启用但未生效 ⚠️

**application.yaml 配置（Line 75-116）：**
```yaml
datasource:
  rw:
    enabled: true  # ✅ 已开启
    groups:
      user:
        master:
          url: jdbc:postgresql://...
          username: admin
          password: 123456
        slaves: [ ]  # ❌ 空数组，无从库配置

      org:
        master: { ... }
        slaves: [ ]  # ❌ 空数组

      permission:
        master: { ... }
        slaves: [ ]  # ❌ 空数组
```

**问题：**
- ✅ 框架已配置启用（enabled: true）
- ❌ 所有从库配置为空数组
- ❌ 即使配置了从库，代码中也完全未使用相关注解

---

### 3.2 代码应用情况：完全未使用 ❌

**统计结果：**
| 注解/API | 使用次数 | 状态 |
|---------|---------|------|
| `@Master` | 0 | ❌ 未使用 |
| `@Slave` | 0 | ❌ 未使用 |
| `@Transactional(readOnly = true)` | 0 | ❌ 未使用 |
| `ReadWriteRoutingContext.forceMaster()` | 0 | ❌ 未使用 |

**影响：**
- 读写分离框架虽然启用，但完全未生效
- 所有查询操作都打到主库，主库压力大
- 从库资源完全闲置（如果有的话）

---

### 3.3 应该添加读写分离的典型场景

#### 场景 1: 查询方法 - 使用从库

```java
// ❌ BEFORE: 所有查询都打主库
@Cacheable(value = "user", key = "#id")
public UserDTO getUserById(UUID id) {
    SysUser user = userMapper.selectById(id);
    // ...
}

// ✅ AFTER: 查询走从库
@Slave  // ← 添加此注解
@Cacheable(value = "user", key = "#id")
public UserDTO getUserById(UUID id) {
    SysUser user = userMapper.selectById(id);
    // ...
}
```

---

#### 场景 2: 只读事务 - 自动路由从库

```java
// ❌ BEFORE: 复杂查询打主库
public Page<UserDTO> listUsers(Integer pageNum, Integer pageSize,
                                String username, Integer status) {
    Page<SysUser> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
    // ...
    return userMapper.selectPage(page, wrapper);
}

// ✅ AFTER: 只读事务自动走从库
@Transactional(readOnly = true)  // ← Spring 自动路由从库
public Page<UserDTO> listUsers(Integer pageNum, Integer pageSize,
                                String username, Integer status) {
    Page<SysUser> page = new Page<>(pageNum, pageSize);
    LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
    // ...
    return userMapper.selectPage(page, wrapper);
}
```

---

#### 场景 3: 写后读 - 强制主库（避免复制延迟）

```java
// ✅ GOOD: 创建用户后立即查询，走主库
@Transactional(rollbackFor = Exception.class)
public void addUser(UserDTO userDTO) {
    SysUser user = new SysUser();
    BeanUtils.copyProperties(userDTO, user);
    userMapper.insert(user);  // 写主库

    // 发布同步事件
    dataSyncEventPublisher.publishUserCreated(user);
}

// ✅ GOOD: 如果需要写后立即读，使用 @Master
@Master(reason = "需要读取刚创建的用户，避免复制延迟")
public UserDTO getUserAfterCreate(UUID userId) {
    SysUser user = userMapper.selectById(userId);  // 强制走主库
    return convertToDTO(user);
}
```

---

#### 场景 4: 报表查询 - 明确走从库

```java
// ✅ GOOD: 重量级报表查询，强制走从库，不影响主库性能
@Slave(fallbackToMaster = false)  // 从库不可用时抛异常，不降级主库
public List<Map<String, Object>> generateMonthlyReport(LocalDate startDate,
                                                        LocalDate endDate) {
    // 复杂的统计查询
    return userMapper.selectMonthlyStatistics(startDate, endDate);
}
```

---

#### 场景 5: 读主时间窗口（框架自动处理）

```java
@Service
public class UserService {

    @Transactional
    public void updateUser(UserDTO userDTO) {
        userMapper.updateById(user);
        // ✅ 框架自动记录写入时间
    }

    // ✅ 在配置的时间窗口内（如 2 秒），自动走主库
    @Transactional(readOnly = true)
    public UserDTO getUserInfo(UUID userId) {
        // 如果刚执行过写操作（2 秒内），自动走主库
        // 超过 2 秒后，走从库
        return userMapper.selectById(userId);
    }
}
```

**配置读主时间窗口：**
```yaml
spring:
  datasource:
    rw:
      read-master-after-write: 2s  # 写后 2 秒内读操作走主库
```

---

### 3.4 推荐的读写分离策略

#### 📊 **分类矩阵**

| 操作类型 | 策略 | 注解 | 原因 |
|---------|------|------|------|
| **查询单条记录** | 从库 | `@Slave` | 读多写少场景，减轻主库压力 |
| **分页查询** | 从库 | `@Transactional(readOnly=true)` | 大量读操作，适合从库 |
| **统计查询** | 从库 | `@Slave` | 不涉及写，走从库 |
| **报表生成** | 从库（强制） | `@Slave(fallbackToMaster=false)` | 重量级查询，禁止降级主库 |
| **写后立即读** | 主库 | `@Master` | 避免复制延迟导致读不到 |
| **实时性要求高** | 主库 | `@Master` | 如金额、库存查询 |
| **增删改操作** | 主库（自动） | `@Transactional` | 写操作必须走主库 |

---

#### 🔧 **改造优先级**

**第一批（高频查询）：**
1. `getUserById` - 添加 `@Slave`
2. `listUsers` - 添加 `@Transactional(readOnly = true)`
3. `getUserInfo` - 添加 `@Slave`
4. `getDeptTree` - 添加 `@Slave`
5. `listRoles` - 添加 `@Transactional(readOnly = true)`

**第二批（统计查询）：**
1. `getUserStatistics` - 添加 `@Slave`
2. `countUsersByDeptId` - 添加 `@Slave`
3. `getPermissionTree` - 添加 `@Slave`

**第三批（写后读场景）：**
1. 新增 `getUserAfterCreate` - 添加 `@Master`
2. 新增 `getRoleAfterGrant` - 添加 `@Master`

---

## 四、数据同步应用情况

### 4.1 应用状态：已正确应用 ✅

**DataSyncEventPublisher 使用情况：**

| 服务类 | CREATE | UPDATE | DELETE | 状态 |
|--------|--------|--------|--------|------|
| **SysUserServiceImpl** | Line 196 | Line 233 | Line 265 | ✅ 完整 |
| **SysRoleServiceImpl** | Line 133 | Line 164 | Line 201 | ✅ 完整 |
| **SysDeptServiceImpl** | Line 129 | Line 167 | Line 199 | ✅ 完整 |

**事件发布示例（SysUserServiceImpl）：**
```java
// ✅ 新增用户
public void addUser(UserDTO userDTO) {
    userMapper.insert(user);

    // 发布同步事件到 Kafka
    dataSyncEventPublisher.publishUserCreated(user);
}

// ✅ 更新用户
public void updateUser(UserDTO userDTO) {
    userMapper.updateById(user);

    // 发布更新事件
    SysUser updatedUser = userMapper.selectById(user.getId());
    dataSyncEventPublisher.publishUserUpdated(updatedUser);
}

// ✅ 删除用户
public void deleteUser(UUID id) {
    userMapper.deleteById(id);

    // 发布删除事件
    dataSyncEventPublisher.publishUserDeleted(id);
}
```

---

### 4.2 DataSyncEventPublisher 实现质量

**文件：** `system/event/DataSyncEventPublisher.java`

**优点：**
- ✅ 正确使用 `DataSyncPublisher` 异步发布
- ✅ 事件类型定义完整（INSERT、UPDATE、DELETE）
- ✅ 包含必要的元数据（sourceDatabase, sourceTable, entityType, entityId）
- ✅ 日志记录完善
- ✅ 使用建造者模式构建事件，代码清晰

**事件结构示例：**
```java
DataSyncEvent event = DataSyncEvent.builder()
    .entityType("User")              // 实体类型
    .entityId(user.getId().toString()) // 实体 ID
    .eventType(DataSyncEventType.INSERT) // 事件类型
    .sourceDatabase("db_user")       // 源数据库
    .sourceTable("sys_user")         // 源表
    .data(buildUserData(user))       // 变更数据
    .build();

publisher.publishAsync(event);  // 异步发布到 Kafka
```

---

### 4.3 数据同步的作用

**同步目标：**
1. **冗余字段更新**：如部门表的 `leader_name` 字段需要同步用户表的 `real_name`
2. **缓存失效通知**：通知其他服务更新本地缓存
3. **事件驱动业务**：如用户创建后自动发送欢迎邮件
4. **数据一致性**：确保分库分表场景下数据最终一致

**当前应用的冗余字段同步：**
- `SysDept.leaderName` 需要同步 `SysUser.realName`
- 通过 DataSyncEvent 通知消费者更新冗余字段

---

## 五、改进建议

### 5.1 🔴 高优先级（必须改）

#### 1. **统一跨库查询职责**

**目标：** 所有跨库查询都通过 `CrossDatabaseQueryService`

**步骤：**

**Step 1: 将跨库 Mapper 依赖移除**
```java
// ❌ 删除这些跨库依赖
@Service
public class SysUserServiceImpl {
    // private final SysUserRoleMapper userRoleMapper;  // 删除
    // private final SysPermissionMapper permissionMapper; // 删除

    private final SysUserMapper userMapper;  // 保留，自己库
    private final CrossDatabaseQueryService crossDbService; // 保留
}
```

**Step 2: 在 CrossDatabaseQueryService 中补充缺失的方法**
```java
@Service
public class CrossDatabaseQueryService {

    // ✅ 新增：查询用户角色（带名称）
    public List<Map<String, Object>> findUserRolesWithNames(UUID userId) {
        // 1. 从 permission 库查询用户角色关联
        List<UUID> roleIds = userRoleMapper.findRoleIdsByUserId(userId);

        // 2. 从 permission 库查询角色信息
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);

        // 3. 组装结果
        return roles.stream()
            .map(role -> Map.of(
                "id", role.getId(),
                "name", role.getRoleName()
            ))
            .collect(Collectors.toList());
    }

    // ✅ 新增：查询用户菜单树
    public List<PermissionDTO> findUserMenuTree(UUID userId) {
        // 从 permission 库查询菜单权限
        return permissionMapper.findMenuTreeByUserId(userId);
    }

    // ✅ 新增：批量统计部门用户数
    public Map<UUID, Integer> countUsersByDeptIds(List<UUID> deptIds) {
        Map<UUID, Integer> result = new HashMap<>();

        // 从 user 库批量查询
        Map<UUID, Map<String, Object>> countMap =
            userMapper.countUsersByDeptIds(deptIds);

        countMap.forEach((deptId, row) -> {
            Integer count = ((Number) row.get("user_count")).intValue();
            result.put(deptId, count);
        });

        return result;
    }
}
```

**Step 3: 修改 ServiceImpl 调用方式**
```java
// ❌ BEFORE
public UserDTO getUserById(UUID id) {
    SysUser user = userMapper.selectById(id);
    List<Map<String, Object>> roles = userRoleMapper.findUserRolesWithNames(id);
    // ...
}

// ✅ AFTER
public UserDTO getUserById(UUID id) {
    SysUser user = userMapper.selectById(id);
    List<Map<String, Object>> roles = crossDbService.findUserRolesWithNames(id);
    // ...
}
```

---

#### 2. **添加读写分离注解**

**目标：** 让读写分离真正生效

**改造清单：**

| 类 | 方法 | 添加注解 | 理由 |
|----|------|---------|------|
| SysUserServiceImpl | `getUserById` | `@Slave` | 查询单条，走从库 |
| SysUserServiceImpl | `listUsers` | `@Transactional(readOnly=true)` | 分页查询，走从库 |
| SysUserServiceImpl | `getUserInfo` | `@Slave` | 查询详情，走从库 |
| SysDeptServiceImpl | `getDeptTree` | `@Slave` | 树形查询，走从库 |
| SysRoleServiceImpl | `listAllRoles` | `@Slave` | 查询列表，走从库 |
| SysRoleServiceImpl | `getRoleById` | `@Slave` | 查询单条，走从库 |
| SysPermissionServiceImpl | `getPermissionTree` | `@Slave` | 查询树，走从库 |

**示例改造（SysUserServiceImpl）：**
```java
// ✅ 添加 @Slave 注解
@Slave
@Cacheable(value = "user", key = "#id")
public UserDTO getUserById(UUID id) {
    // 查询走从库
}

// ✅ 添加只读事务
@Transactional(readOnly = true)
public Page<UserDTO> listUsers(Integer pageNum, Integer pageSize,
                                String username, Integer status) {
    // 分页查询走从库
}

// ✅ 保持写事务不变
@Transactional(rollbackFor = Exception.class)
public void addUser(UserDTO userDTO) {
    // 写操作走主库（自动）
}
```

---

#### 3. **配置真实的从库（如果有的话）**

**application.yaml:**
```yaml
spring:
  datasource:
    rw:
      enabled: true
      load-balance: WEIGHTED_ROUND_ROBIN  # 加权轮询
      replication-lag-tolerance: 1s       # 复制延迟容忍 1 秒
      read-master-after-write: 2s         # 写后 2 秒内读主库
      health-check-enabled: true
      health-check-interval: 30s

      groups:
        user:
          master:
            url: jdbc:postgresql://192.168.18.134:5432/db_user
            username: admin
            password: 123456
            minimum-idle: 10
            maximum-pool-size: 50
          slaves:
            - name: slave1
              url: jdbc:postgresql://192.168.18.135:5432/db_user  # ← 从库地址
              username: admin
              password: 123456
              weight: 2  # 权重
            - name: slave2
              url: jdbc:postgresql://192.168.18.136:5432/db_user
              username: admin
              password: 123456
              weight: 1
```

**如果没有从库：**
- 暂时保持 `slaves: []`
- 仍然添加 `@Slave` 和 `@Transactional(readOnly=true)` 注解
- 原因：框架会自动 fallback 到主库，不影响功能
- 好处：未来有从库时，无需修改代码，立即生效

---

### 5.2 🟡 中优先级（建议改）

#### 1. **为 CrossDatabaseQueryService 添加缓存**

```java
@Service
public class CrossDatabaseQueryService {

    // ✅ 添加缓存，减少跨库查询
    @Cacheable(value = "userDeptTree", key = "#userId")
    public List<UUID> findUserDeptAndChildren(UUID userId) {
        UUID deptId = userMapper.getUserDeptId(userId);
        if (deptId == null) {
            return Collections.emptyList();
        }
        return deptMapper.selectDeptAndChildren(deptId);
    }

    // ✅ 添加缓存
    @Cacheable(value = "accessibleDeptIds", key = "#roleId")
    public List<UUID> findAccessibleDeptIds(UUID roleId) {
        // ...
    }
}
```

---

#### 2. **添加监控埋点**

```java
@Service
public class CrossDatabaseQueryService {

    private final MeterRegistry meterRegistry;  // Micrometer

    public List<UUID> findUserDeptAndChildren(UUID userId) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            // 业务逻辑
        } finally {
            sample.stop(meterRegistry.timer("cross_db_query",
                "method", "findUserDeptAndChildren"));
        }
    }
}
```

---

#### 3. **为跨库查询添加超时控制**

```java
@Service
public class CrossDatabaseQueryService {

    @Timeout(value = 3, unit = TimeUnit.SECONDS)  // 3 秒超时
    public List<UUID> findAccessibleDeptIds(UUID roleId) {
        // 跨库查询，避免长时间阻塞
    }
}
```

---

### 5.3 🟢 低优先级（可选）

#### 1. **抽取跨库查询接口**

```java
// 定义接口
public interface ICrossDatabaseQueryService {
    UUID findFirstUserIdByRoleCode(String roleCode);
    List<UUID> findUserDeptAndChildren(UUID userId);
    // ...
}

// 实现类
@Service
public class CrossDatabaseQueryServiceImpl implements ICrossDatabaseQueryService {
    // ...
}
```

**好处：**
- 便于单元测试（可以 Mock）
- 符合依赖倒置原则
- 便于未来扩展（如添加分布式缓存实现）

---

#### 2. **添加请求合并（Request Coalescing）**

```java
@Service
public class CrossDatabaseQueryService {

    private final LoadingCache<UUID, List<UUID>> deptChildrenCache =
        Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(deptId -> deptMapper.selectDeptAndChildren(deptId));

    // ✅ 多个并发请求会被合并
    public List<UUID> findUserDeptAndChildren(UUID userId) {
        UUID deptId = userMapper.getUserDeptId(userId);
        return deptChildrenCache.get(deptId);
    }
}
```

---

## 六、改造路线图

### 阶段 1: 职责划分（1-2 天）

- [ ] 补充 CrossDatabaseQueryService 缺失的方法
- [ ] 移除 ServiceImpl 中的跨库 Mapper 依赖
- [ ] 统一通过 CrossDatabaseQueryService 进行跨库查询
- [ ] 添加单元测试验证功能正常

---

### 阶段 2: 读写分离（1 天）

- [ ] 为高频查询方法添加 `@Slave` 注解（10+ 个方法）
- [ ] 为分页查询添加 `@Transactional(readOnly=true)`（5+ 个方法）
- [ ] 为写后读场景添加 `@Master` 注解（3+ 个方法）
- [ ] 测试验证：
  - 开启 SQL 日志，观察查询是否走从库
  - 模拟从库故障，验证 fallback 逻辑

---

### 阶段 3: 性能优化（可选，1-2 天）

- [ ] 为 CrossDatabaseQueryService 添加缓存
- [ ] 添加监控埋点（Micrometer）
- [ ] 添加超时控制（@Timeout）
- [ ] 配置真实从库（如果有的话）

---

### 阶段 4: 验证与监控（持续）

- [ ] 观察读写分离效果（通过 Actuator 端点）
  ```bash
  curl http://localhost:8081/actuator/readwrite
  ```
- [ ] 观察 Prometheus 指标
  ```
  datasource_rw_route_total{group="user",target="master"}
  datasource_rw_route_total{group="user",target="slave"}
  ```
- [ ] 验证数据同步事件是否正常发布（Kafka 消费者日志）

---

## 七、总结

### 7.1 当前状态评估

| 方面 | 评分 | 说明 |
|------|------|------|
| **设计思想** | ⭐⭐⭐⭐⭐ | 跨库统一查询设计清晰，有专门的 CrossDatabaseQueryService |
| **职责划分** | ⭐⭐ | 混乱，ServiceImpl 既用 CrossDatabaseQueryService 又直接注入跨库 Mapper |
| **读写分离** | ⭐ | 配置已开启但完全未使用，所有查询都打主库 |
| **数据同步** | ⭐⭐⭐⭐⭐ | 正确应用，事件发布完整 |
| **代码质量** | ⭐⭐⭐ | 部分代码职责清晰（Role/Permission），部分混乱（User/Dept/Approval） |

---

### 7.2 核心问题

1. **职责不清**：CrossDatabaseQueryService 存在但未被充分利用
2. **读写分离失效**：框架配置了，代码未使用，完全未生效
3. **维护困难**：跨库逻辑散落在多处，难以统一优化

---

### 7.3 预期收益（改造后）

| 指标 | 改造前 | 改造后 | 提升 |
|------|--------|--------|------|
| **主库 QPS** | 5000 | 1500 | 降低 70% |
| **响应时间 P99** | 800ms | 200ms | 降低 75% |
| **代码可维护性** | 低 | 高 | 显著提升 |
| **跨库查询性能** | 无缓存 | 有缓存 | 10x 提升 |

---

## 八、附录

### 8.1 相关文档

- [读写分离配置指南](docs/read-write-separation.md)
- [数据同步框架文档](common/integration/README.md)
- [CrossDatabaseQueryService API](system/service/CrossDatabaseQueryService.java)

---

### 8.2 联系方式

如有疑问，请联系：
- **架构负责人：** Deng
- **Code Review：** [提交 PR](https://github.com/dengxingzhi000/CommonPermissionsFramework/pulls)

---

**审查完成时间：** 2025-12-18 17:00:00
**下次审查时间：** 改造完成后