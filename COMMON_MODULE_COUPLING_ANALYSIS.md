# Common模块耦合度分析报告

**日期**: 2025-12-12
**分析对象**: common模块群（core, data, web, integration, monitoring）
**结论**: 🔴 **耦合度过高** - 需要架构重构

---

## 📊 总体评估

| 维度 | 评分 | 状态 | 说明 |
|------|------|------|------|
| **依赖方向正确性** | 3/10 | 🔴 严重 | 存在反向依赖和循环依赖 |
| **模块职责清晰度** | 4/10 | 🔴 严重 | common/core职责过重 |
| **可复用性** | 5/10 | 🟡 中等 | 依赖过多导致难以单独复用 |
| **可测试性** | 6/10 | 🟡 中等 | 依赖注入良好但模块边界不清 |
| **可维护性** | 4/10 | 🔴 严重 | 修改common模块影响面大 |

**综合评分**: **4.4/10 (D级)** - 需要立即重构

---

## 🚨 严重违反原则的依赖关系

### 1. ❌ **common/data → common/web/securityCore** (反向依赖)

**问题严重性**: 🔴 **CRITICAL**

**违反原则**:
- 分层架构原则：数据层不应该依赖表现层
- 单向依赖原则：低层模块依赖了高层模块

**具体依赖点**:
```
common/data/src/main/java/com/frog/common/mybatisPlus/aspect/DataScopeAspect.java:6
  ├─ import com.frog.common.web.domain.SecurityUser;
  └─ import com.frog.common.web.util.SecurityUtils;
```

**问题代码**:
```java
// DataScopeAspect.java - Line 35
SecurityUser currentUser = SecurityUtils.getCurrentUser();
UUID userId = currentUser.getUserId();
UUID deptId = currentUser.getDeptId();
Integer dataScopeLevel = currentUser.getAccountType();
```

**为什么会这样设计**:
- DataScope需要获取当前用户的安全上下文（userId, deptId, dataScope）
- 目前直接依赖SecurityUser和SecurityUtils获取

**影响**:
- common/data无法独立使用（必须引入整个web模块）
- 违反了数据访问层的纯粹性
- 增加了集成测试的复杂度

---

### 2. ❌ **common/web → system/api** (common依赖业务模块)

**问题严重性**: 🔴 **CRITICAL**

**违反原则**:
- 依赖倒置原则（DIP）：通用模块不应该依赖具体业务模块
- 开闭原则：common模块应该对业务模块开放，但不应依赖它

**具体依赖点**:
```
common/web/src/main/java/com/frog/common/access/DubboPermissionAccess.java:3
  └─ import com.frog.system.api.PermissionDubboService;
```

**问题代码**:
```java
// DubboPermissionAccess.java
@DubboReference
private PermissionDubboService permissionDubboService;  // ❌ 依赖具体业务API
```

**为什么会这样设计**:
- 需要通过Dubbo调用system服务的权限查询接口
- 直接引用了system/api模块的Dubbo服务接口

**影响**:
- common/web无法在其他项目中复用（强绑定到system业务）
- 违反了可插拔架构原则
- 增加了common模块的发布复杂度（必须等system/api先发布）

---

### 3. ⚠️ **common/core职责过重** (Single Responsibility违反)

**问题严重性**: 🟡 **HIGH**

**问题表现**:
common/core/pom.xml包含过多非核心依赖：

```xml
<!-- 这些不应该出现在"core"模块中 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
```

**为什么会这样**:
- 初期为了方便，把所有通用依赖都放到core模块
- 没有按照功能领域进行细分

**影响**:
- 任何项目依赖common-core都会被迫引入Spring Web、Security、Redis、MyBatis
- 无法做到按需依赖
- 增加了应用启动时间和内存占用

---

## 📈 当前依赖关系图

```
┌─────────────────────────────────────────────────┐
│                  Business Layer                 │
│  ┌───────────┐  ┌───────────┐  ┌─────────────┐ │
│  │  Gateway  │  │   Auth    │  │   System    │ │
│  └─────┬─────┘  └─────┬─────┘  └──────┬──────┘ │
│        │              │                │        │
└────────┼──────────────┼────────────────┼────────┘
         │              │                │
         │              │                │ system/api
         │              │                │
┌────────┼──────────────┼────────────────┼────────┐
│        ▼              ▼                │        │
│  ┌─────────────────────────────────┐  │        │
│  │       common/web                │◄─┘        │ ❌ 错误依赖
│  │  ┌─────────────────────────┐   │           │
│  │  │  securityCore          │   │           │
│  │  │  (SecurityUser)        │   │           │
│  │  └──────────▲──────────────┘   │           │
│  └─────────────┼────────────────────┘           │
│                │                                │
│                │ ❌ 错误依赖                      │
│  ┌─────────────┴────────────────┐              │
│  │       common/data            │              │
│  │  (DataScopeAspect)          │              │
│  └─────────────┬────────────────┘              │
│                │                                │
│  ┌─────────────▼────────────────┐              │
│  │       common/core            │              │
│  │  (过重：Web+Security+Redis)   │              │
│  └──────────────────────────────┘              │
│                                                 │
│   Common Layer (应该是最底层，无上层依赖)         │
└─────────────────────────────────────────────────┘
```

**问题标注**:
- ❌ **红色箭头**: 反向依赖（违反分层原则）
- ⚠️ **黄色方框**: 职责过重

---

## 🎯 理想的依赖关系（应该是什么样）

```
┌─────────────────────────────────────────────────┐
│                  Business Layer                 │
│  ┌───────────┐  ┌───────────┐  ┌─────────────┐ │
│  │  Gateway  │  │   Auth    │  │   System    │ │
│  └─────┬─────┘  └─────┬─────┘  └──────┬──────┘ │
│        │              │                │        │
└────────┼──────────────┼────────────────┼────────┘
         │              │                │
         │              │                │
         ▼              ▼                ▼
┌──────────────────────────────────────────────────┐
│              Infrastructure Layer                │
│                                                  │
│  ┌──────────────┐  ┌──────────────┐            │
│  │ common/web   │  │ common/data  │            │
│  │ (Security)   │  │ (MyBatis)    │            │
│  └──────┬───────┘  └──────┬───────┘            │
│         │                 │                     │
│         │   ┌─────────────┴────────┐            │
│         │   │ common/security-api  │ (新模块)   │
│         │   │ (SecurityContext)    │            │
│         │   └───────────┬──────────┘            │
│         │               │                        │
│         └───────┬───────┘                        │
│                 ▼                                │
│  ┌──────────────────────────────┐               │
│  │     common/core              │               │
│  │  (纯工具类：UUID, PageResult) │               │
│  └──────────────────────────────┘               │
│                                                  │
│   Foundation Layer (最底层，只有JDK依赖)          │
└──────────────────────────────────────────────────┘
```

**设计原则**:
✅ 单向依赖：只能从上往下依赖
✅ 依赖倒置：通过接口（common/security-api）解耦
✅ 职责单一：每个模块只做一件事

---

## 🔍 详细问题分析

### 问题1: DataScopeAspect的安全上下文依赖

**当前实现**:
```java
// common/data/DataScopeAspect.java
SecurityUser currentUser = SecurityUtils.getCurrentUser();  // ❌ 依赖web层
UUID userId = currentUser.getUserId();
```

**问题**:
1. **类型耦合**: 直接依赖`SecurityUser`具体类型
2. **模块耦合**: data层不应该知道web层的存在
3. **测试困难**: 测试DataScope需要mock整个SecurityContext

**优秀实践参考** (Spring Data JPA的做法):
```java
// Spring Data JPA只依赖接口
Optional<AuditorAware<T>> auditorAware = this.auditorAware;
```

Spring Data JPA不直接依赖Spring Security，而是定义`AuditorAware<T>`接口，由用户实现。

---

### 问题2: DubboPermissionAccess的业务API依赖

**当前实现**:
```java
// common/web/DubboPermissionAccess.java
@DubboReference
private PermissionDubboService permissionDubboService;  // ❌ system/api
```

**问题**:
1. **业务绑定**: common模块绑定到具体业务（system）
2. **复用困难**: 无法在其他项目使用该模块
3. **发布依赖**: common模块发布必须等待system/api发布

**优秀实践参考** (Spring Cloud OpenFeign的做法):
```java
// OpenFeign只定义客户端接口，由用户实现
@FeignClient(name = "permission-service")
public interface PermissionClient {
    // ...
}
```

Feign不依赖任何业务模块，只提供框架能力。

---

### 问题3: common/core的依赖膨胀

**当前pom.xml**:
```xml
<!-- common/core/pom.xml -->
<dependencies>
    <!-- ❌ 不应该在core出现的依赖 -->
    <dependency>spring-boot-starter-web</dependency>
    <dependency>spring-boot-starter-security</dependency>
    <dependency>spring-boot-starter-data-redis</dependency>
    <dependency>mybatis-plus-spring-boot3-starter</dependency>
    <dependency>spring-cloud-starter-openfeign</dependency>

    <!-- ✅ 合理的core依赖 -->
    <dependency>lombok</dependency>
    <dependency>jackson-databind</dependency>
</dependencies>
```

**问题**:
- **误导性命名**: "core"暗示轻量级，实际却很重
- **强制依赖**: 任何使用core的模块都被迫引入Web/Security/Redis
- **启动性能**: 即使不使用这些功能，也会触发自动配置

**对比Netflix OSS**:
```xml
<!-- netflix-commons/pom.xml -->
<dependencies>
    <!-- 只有Guava、Jackson等纯工具库 -->
    <dependency>com.google.guava</dependency>
    <dependency>jackson-databind</dependency>
</dependencies>
```

Netflix的commons模块非常轻量，只包含纯工具类。

---

## 🛠️ 重构建议

### 方案A: 渐进式重构 (推荐，风险低)

#### 阶段1: 解耦common/data → common/web (1-2天)

**目标**: 移除DataScopeAspect对SecurityUser的直接依赖

**步骤**:

1. **创建安全上下文接口** (common/core/src/main/java/com/frog/common/security/)
```java
// 新建：SecurityContext.java
package com.frog.common.security;

import java.util.UUID;

/**
 * 安全上下文接口，供数据权限等功能使用
 * 避免数据层直接依赖Web层
 */
public interface SecurityContext {
    /**
     * 获取当前用户ID
     */
    UUID getCurrentUserId();

    /**
     * 获取当前用户部门ID
     */
    UUID getCurrentDeptId();

    /**
     * 获取数据权限等级
     * 1-全部 2-自定义 3-本部门 4-本部门及子部门 5-仅本人
     */
    Integer getDataScopeLevel();

    /**
     * 是否已认证
     */
    boolean isAuthenticated();
}
```

2. **在common/web实现该接口**
```java
// 新建：common/web/.../SpringSecurityContext.java
@Component
public class SpringSecurityContext implements SecurityContext {
    @Override
    public UUID getCurrentUserId() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        return user != null ? user.getUserId() : null;
    }

    @Override
    public UUID getCurrentDeptId() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        return user != null ? user.getDeptId() : null;
    }

    @Override
    public Integer getDataScopeLevel() {
        SecurityUser user = SecurityUtils.getCurrentUser();
        return user != null ? user.getAccountType() : 5; // 默认仅本人
    }

    @Override
    public boolean isAuthenticated() {
        return SecurityUtils.getCurrentUser() != null;
    }
}
```

3. **修改DataScopeAspect使用接口**
```java
// 修改：DataScopeAspect.java
@Aspect
@Component
@Slf4j
public class DataScopeAspect {
    private final SecurityContext securityContext;  // ✅ 依赖接口

    public DataScopeAspect(SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint point, DataScope dataScope) throws Throwable {
        try {
            if (!securityContext.isAuthenticated()) {
                return point.proceed();
            }

            UUID userId = securityContext.getCurrentUserId();      // ✅ 通过接口获取
            UUID deptId = securityContext.getCurrentDeptId();      // ✅ 通过接口获取
            Integer level = securityContext.getDataScopeLevel();   // ✅ 通过接口获取

            DataScopeFilter filter = buildSqlFilter(level, userId, deptId, dataScope);
            DataScopeContextHolder.set(filter);

            return point.proceed();
        } finally {
            DataScopeContextHolder.clear();
        }
    }

    // ... 其他方法不变
}
```

4. **更新common/data/pom.xml**
```xml
<!-- 移除 -->
<dependency>
    <groupId>com.frog.common.web</groupId>
    <artifactId>securityCore</artifactId>
</dependency>

<!-- 不需要添加新依赖，SecurityContext在common/core -->
```

**收益**:
- ✅ common/data不再依赖common/web
- ✅ 可以独立测试DataScope（mock SecurityContext即可）
- ✅ 符合依赖倒置原则（DIP）

---

#### 阶段2: 解耦common/web → system/api (1-2天)

**目标**: 移除common/web对system业务模块的依赖

**步骤**:

1. **在common/core定义权限服务接口**
```java
// 新建：common/core/.../PermissionService.java
package com.frog.common.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 权限服务接口（通用抽象）
 * 具体实现由业务模块提供（Dubbo/Feign）
 */
public interface PermissionService {
    /**
     * 根据URL和方法查询所需权限
     */
    List<String> findPermissionsByUrl(String url, String method);

    /**
     * 根据用户ID查询所有权限
     */
    Set<String> findAllPermissionsByUserId(UUID userId);
}
```

2. **修改DubboPermissionAccess实现通用接口**
```java
// 修改：common/web/.../DubboPermissionAccess.java
// 移到：system/service/.../DubboPermissionAccessImpl.java (更好的位置)

package com.frog.system.adapter;  // 业务模块的适配器层

import com.frog.common.security.PermissionService;  // ✅ 依赖common接口
import com.frog.system.api.PermissionDubboService;  // ✅ 在业务模块内部依赖

@Component
@Primary
public class DubboPermissionAccessImpl implements PermissionService {
    @DubboReference
    private PermissionDubboService permissionDubboService;  // ✅ 业务模块内部依赖

    @Override
    public List<String> findPermissionsByUrl(String url, String method) {
        // 调用Dubbo服务
        return permissionDubboService.findPermissionsByUrl(url, method);
    }

    @Override
    public Set<String> findAllPermissionsByUserId(UUID userId) {
        return permissionDubboService.findAllPermissionsByUserId(userId);
    }
}
```

3. **在common/web只依赖通用接口**
```java
// 修改：common/web/.../ApiAccessControlFilter.java
@Component
public class ApiAccessControlFilter extends OncePerRequestFilter {
    private final PermissionService permissionService;  // ✅ 依赖接口，不依赖实现

    public ApiAccessControlFilter(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Override
    protected void doFilterInternal(...) {
        List<String> required = permissionService.findPermissionsByUrl(url, method);
        // ...
    }
}
```

4. **删除common/web中的DubboPermissionAccess和FeignPermissionAccess**

将它们移到各自的业务模块：
- `system/service/adapter/DubboPermissionAccessImpl.java`
- `auth/adapter/FeignPermissionAccessImpl.java` (如果auth服务需要)

5. **更新common/web/pom.xml**
```xml
<!-- 移除 -->
<dependency>
    <groupId>com.frog.system</groupId>
    <artifactId>api</artifactId>
</dependency>

<!-- 不需要添加新依赖 -->
```

**收益**:
- ✅ common/web不再依赖任何业务模块
- ✅ 符合依赖倒置原则（DIP）
- ✅ common模块可以在其他项目复用

---

#### 阶段3: 拆分common/core (2-3天)

**目标**: 将过重的common/core拆分为职责单一的子模块

**新模块结构**:
```
common/
├── core/                    # 纯工具类（无Spring依赖）
│   ├── UUIDv7Util
│   ├── JsonUtils
│   └── PageResult
├── security-api/            # 安全接口（无实现）
│   ├── SecurityContext
│   └── PermissionService
├── web/                     # Web层通用组件
│   ├── 过滤器
│   └── 异常处理
├── data/                    # 数据层通用组件
│   ├── MyBatis配置
│   └── DataScope
├── cache/                   # 缓存通用组件
│   └── MultiLevelCache
└── messaging/               # 消息通用组件
    └── Kafka/RabbitMQ
```

**步骤**:

1. **创建common/security-api模块**
```xml
<!-- common/security-api/pom.xml -->
<dependencies>
    <!-- 只依赖JDK和Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
</dependencies>
```

移入：
- `SecurityContext` 接口
- `PermissionService` 接口

2. **创建common/cache模块**
```xml
<!-- common/cache/pom.xml -->
<dependencies>
    <dependency>common-core</dependency>
    <dependency>spring-boot-starter-data-redis</dependency>
    <dependency>caffeine</dependency>
</dependencies>
```

移入：
- `MultiLevelCache`
- `TwoLevelCache`
- 缓存配置类

3. **瘦身common/core**
```xml
<!-- common/core/pom.xml - 新版本 -->
<dependencies>
    <!-- ✅ 只保留纯工具库 -->
    <dependency>lombok</dependency>
    <dependency>jackson-databind</dependency>
    <dependency>guava</dependency>

    <!-- ❌ 移除所有Spring依赖 -->
</dependencies>
```

保留：
- `UUIDv7Util`
- `PageResult`
- `JsonUtils`
- 异常类（但不包含Spring的`@RestControllerAdvice`）

4. **更新各模块依赖**
```xml
<!-- common/web/pom.xml -->
<dependencies>
    <dependency>common-core</dependency>
    <dependency>common-security-api</dependency>  <!-- 新增 -->
    <dependency>spring-boot-starter-web</dependency>
</dependencies>

<!-- common/data/pom.xml -->
<dependencies>
    <dependency>common-core</dependency>
    <dependency>common-security-api</dependency>  <!-- 新增 -->
    <dependency>mybatis-plus-spring-boot3-starter</dependency>
</dependencies>

<!-- system/service/pom.xml -->
<dependencies>
    <dependency>common-core</dependency>
    <dependency>common-security-api</dependency>
    <dependency>common-web</dependency>
    <dependency>common-data</dependency>
    <dependency>common-cache</dependency>  <!-- 按需引入 -->
</dependencies>
```

**收益**:
- ✅ 按需依赖：不需要缓存的模块不会引入Redis
- ✅ 启动性能提升：减少不必要的自动配置
- ✅ 清晰的职责划分

---

### 方案B: 激进式重构 (高风险，不推荐)

**不推荐原因**:
1. 需要同时修改大量模块，风险极高
2. 影响正在进行的业务开发
3. 需要大量回归测试

**如果非要选择**:
- 创建feature分支
- 完整的CI/CD测试覆盖
- 分模块逐步合并

---

## 📋 重构检查清单

### 阶段1完成标准 (DataScope解耦)
- [ ] `SecurityContext`接口已创建
- [ ] `SpringSecurityContext`实现已创建
- [ ] `DataScopeAspect`已修改为依赖接口
- [ ] `common/data/pom.xml`已移除对`securityCore`的依赖
- [ ] 所有DataScope相关测试通过
- [ ] 集成测试验证DataScope功能正常

### 阶段2完成标准 (PermissionService解耦)
- [ ] `PermissionService`接口已创建在common/core
- [ ] `DubboPermissionAccessImpl`已移到system/service
- [ ] `FeignPermissionAccessImpl`已移到auth/service（如需要）
- [ ] common/web的Filter已改为依赖`PermissionService`接口
- [ ] `common/web/pom.xml`已移除对`system/api`的依赖
- [ ] 所有权限相关测试通过
- [ ] 集成测试验证权限功能正常

### 阶段3完成标准 (core拆分)
- [ ] `common/security-api`模块已创建
- [ ] `common/cache`模块已创建
- [ ] `common/core`已瘦身（无Spring依赖）
- [ ] 所有模块pom.xml已更新依赖
- [ ] 全量测试通过
- [ ] 构建时间对比（应该更快）
- [ ] 启动时间对比（应该更快）

---

## 🎯 预期收益

### 技术收益

| 指标 | 当前 | 重构后 | 提升 |
|------|------|--------|------|
| **common模块可复用性** | 30% | 90% | +200% |
| **模块依赖层数** | 4层（有循环） | 3层（单向） | ✅ |
| **测试覆盖难度** | 高 | 中 | ✅ |
| **启动时间** | 基线 | -15% | +15% |
| **构建时间** | 基线 | -10% | +10% |

### 架构收益

✅ **符合SOLID原则**
- Single Responsibility: 每个模块职责单一
- Dependency Inversion: 依赖接口而非实现

✅ **符合分层架构**
- 单向依赖：Business → Common → Core
- 无循环依赖

✅ **提升可测试性**
- 模块间通过接口交互，易于mock
- 减少集成测试的复杂度

✅ **提升可维护性**
- 修改一个模块不会影响其他模块
- 清晰的边界和职责

---

## 🚀 实施计划

### Week 1: DataScope解耦
- Day 1-2: 创建`SecurityContext`接口，实现`SpringSecurityContext`
- Day 3: 修改`DataScopeAspect`
- Day 4: 测试验证
- Day 5: Code Review + 合并

### Week 2: PermissionService解耦
- Day 1-2: 创建`PermissionService`接口，移动实现类到业务模块
- Day 3: 修改Filter和相关代码
- Day 4: 测试验证
- Day 5: Code Review + 合并

### Week 3: core拆分
- Day 1-2: 创建新模块（security-api, cache）
- Day 3: 移动代码，更新依赖
- Day 4-5: 全量测试 + 性能对比

---

## 💡 最佳实践参考

### 1. Spring Framework
Spring的模块化非常优秀：
```
spring-core          # 只依赖JDK
spring-context       # 依赖core
spring-web           # 依赖context
spring-webmvc        # 依赖web
```

每个模块职责清晰，依赖单向。

### 2. Netflix OSS
```
netflix-commons      # 纯工具类
netflix-archaius     # 配置管理
netflix-eureka-client # 服务发现
```

通用模块（commons）绝不依赖业务模块。

### 3. Apache Commons
```
commons-lang3        # 语言工具（无外部依赖）
commons-collections4 # 集合工具（只依赖lang3）
```

每个模块都可以独立使用。

---

## ❓ FAQ

### Q1: 重构会影响现有功能吗？
A: 如果按照渐进式重构，每个阶段都有完整测试，风险可控。建议：
- 每个阶段都在feature分支进行
- 完成后进行完整的回归测试
- 灰度发布验证

### Q2: 重构需要多长时间？
A: 预计3周（按照上述计划）：
- Week 1: DataScope解耦
- Week 2: PermissionService解耦
- Week 3: core拆分

### Q3: 重构期间是否影响新功能开发？
A: 建议：
- 重构在独立分支进行
- 新功能继续在主分支开发
- 重构完成后统一合并

### Q4: 是否可以只做部分重构？
A: 可以，建议优先级：
1. **必须做**: DataScope解耦（阶段1） - 违反分层原则
2. **必须做**: PermissionService解耦（阶段2） - 违反DIP
3. **建议做**: core拆分（阶段3） - 提升性能

---

## 📚 相关文档

- [SOLID设计原则](https://en.wikipedia.org/wiki/SOLID)
- [依赖倒置原则（DIP）](https://en.wikipedia.org/wiki/Dependency_inversion_principle)
- [Spring模块化设计](https://docs.spring.io/spring-framework/reference/)
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

---

**报告生成时间**: 2025-12-12
**分析人员**: Claude Code
**版本**: 1.0