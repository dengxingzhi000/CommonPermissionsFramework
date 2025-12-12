# 阶段3重构计划：common/core拆分

**日期**: 2025-12-12
**目标**: 将过重的common/core拆分为职责单一的子模块
**预计时间**: 2-3天

---

## 🎯 重构目标

### 问题分析

**当前common/core的问题**:

```xml
<!-- common/core/pom.xml - 当前依赖 -->
<dependencies>
    <!-- ❌ 不应该在core出现的重量级依赖 -->
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

**影响**:
- ❌ 任何依赖core的模块都被迫引入Spring Web、Security、Redis
- ❌ 即使不使用这些功能，也会触发自动配置
- ❌ 增加启动时间和内存占用
- ❌ "core"名称误导（暗示轻量级，实际很重）

---

## 📐 新模块结构设计

### 目标结构

```
common/
├── core/                           # 纯工具类（无Spring依赖）
│   ├── pom.xml                     # 只依赖JDK + Lombok + Jackson
│   ├── UUIDv7Util.java             # UUID生成工具
│   ├── PageResult.java             # 分页结果
│   ├── JsonUtils.java              # JSON工具
│   └── exception/                  # 异常类（无Spring）
│
├── security-api/                   # 安全接口（新建）
│   ├── pom.xml                     # 只依赖JDK + Lombok
│   ├── SecurityContext.java        # 从core移过来
│   └── PermissionService.java      # 从core移过来
│
├── web/                            # Web层通用组件
│   ├── pom.xml                     # 依赖: core + security-api
│   └── SpringSecurityContext.java  # 实现SecurityContext
│
├── data/                           # 数据层通用组件
│   ├── pom.xml                     # 依赖: core + security-api
│   └── DataScopeAspect.java        # 使用SecurityContext
│
└── monitoring/                     # 监控组件（已存在）
```

---

## 🔄 拆分步骤

### 步骤1: 创建 common/security-api 模块

#### 1.1 创建目录结构

```bash
mkdir -p common/security-api/src/main/java/com/frog/common/security
mkdir -p common/security-api/src/main/resources
```

#### 1.2 创建 pom.xml

```xml
<!-- common/security-api/pom.xml -->
<project>
    <parent>
        <groupId>com</groupId>
        <artifactId>NewNearSync</artifactId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>

    <groupId>com.frog.common</groupId>
    <artifactId>security-api</artifactId>
    <name>security-api</name>

    <dependencies>
        <!-- 只依赖Lombok（轻量级） -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

#### 1.3 移动接口文件

```bash
# 移动 SecurityContext
mv common/core/src/main/java/com/frog/common/security/SecurityContext.java \
   common/security-api/src/main/java/com/frog/common/security/

# 移动 PermissionService
mv common/core/src/main/java/com/frog/common/security/PermissionService.java \
   common/security-api/src/main/java/com/frog/common/security/
```

---

### 步骤2: 瘦身 common/core

#### 2.1 更新 pom.xml

```xml
<!-- common/core/pom.xml - 新版本 -->
<dependencies>
    <!-- ✅ 只保留纯工具库 -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
    </dependency>

    <!-- ❌ 移除所有Spring依赖 -->
    <!-- <dependency>spring-boot-starter-web</dependency> -->
    <!-- <dependency>spring-boot-starter-security</dependency> -->
    <!-- <dependency>spring-boot-starter-data-redis</dependency> -->
    <!-- <dependency>mybatis-plus</dependency> -->
</dependencies>
```

#### 2.2 保留的文件

```
common/core/src/main/java/com/frog/common/
├── domain/
│   └── PageResult.java             # 分页结果（纯POJO）
├── exception/
│   ├── BusinessException.java      # 业务异常（不依赖Spring）
│   └── ErrorCode.java              # 错误码枚举
├── response/
│   └── ApiResponse.java            # API响应（纯POJO）
└── util/
    ├── UUIDv7Util.java             # UUID工具
    └── JsonUtils.java              # JSON工具（只依赖Jackson）
```

#### 2.3 移除的文件

需要删除或移动包含Spring依赖的类：
- ❌ GlobalExceptionHandler.java（移到common/web）
- ❌ 其他包含@RestControllerAdvice等Spring注解的类

---

### 步骤3: 更新依赖模块

#### 3.1 更新 common/data/pom.xml

```xml
<!-- common/data/pom.xml -->
<dependencies>
    <!-- 基础模块 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>core</artifactId>
    </dependency>

    <!-- 新增：安全接口模块 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>security-api</artifactId>
    </dependency>

    <!-- 其他依赖保持不变 -->
</dependencies>
```

#### 3.2 更新 common/web/pom.xml

```xml
<!-- common/web/pom.xml -->
<dependencies>
    <!-- 基础模块 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>core</artifactId>
    </dependency>

    <!-- 新增：安全接口模块 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>security-api</artifactId>
    </dependency>

    <!-- 其他依赖保持不变 -->
</dependencies>
```

#### 3.3 更新 system/service/pom.xml

```xml
<!-- system/service/pom.xml -->
<dependencies>
    <!-- 基础模块 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>core</artifactId>
    </dependency>

    <!-- 新增：安全接口模块 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>security-api</artifactId>
    </dependency>

    <!-- 其他依赖 -->
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.frog.common</groupId>
        <artifactId>data</artifactId>
    </dependency>
</dependencies>
```

---

### 步骤4: 更新父 pom.xml

#### 4.1 添加新模块

```xml
<!-- pom.xml (根目录) -->
<modules>
    <module>common/core</module>
    <module>common/security-api</module>  <!-- 新增 -->
    <module>common/data</module>
    <module>common/web</module>
    <module>common/web/securityCore</module>
    <module>common/monitoring</module>
    <module>common/integration</module>
    <!-- ... 其他模块 -->
</modules>
```

#### 4.2 添加版本管理

```xml
<!-- pom.xml (根目录) - dependencyManagement -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.frog.common</groupId>
            <artifactId>core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.frog.common</groupId>
            <artifactId>security-api</artifactId>  <!-- 新增 -->
            <version>${project.version}</version>
        </dependency>
        <!-- ... -->
    </dependencies>
</dependencyManagement>
```

---

## 📊 预期收益

### 模块大小对比

| 模块 | 重构前依赖 | 重构后依赖 | 改善 |
|------|-----------|-----------|------|
| **common/core** | 15个 | 3个 | ✅ -80% |
| **common/security-api** | - | 1个 | ✅ 新建 |
| **common/data** | 通过core引入15个 | 只引入需要的 | ✅ -60% |
| **common/web** | 通过core引入15个 | 只引入需要的 | ✅ -40% |

---

### 性能提升预估

| 指标 | 重构前 | 重构后 | 提升 |
|------|--------|--------|------|
| **启动时间** | 基线 | -15% | ✅ |
| **构建时间** | 基线 | -10% | ✅ |
| **内存占用** | 基线 | -8% | ✅ |
| **Jar包大小** | 基线 | -12% | ✅ |

---

## ✅ 验证清单

### 编译验证

```bash
# 1. 编译新模块
cd common/security-api && mvn clean install -DskipTests

# 2. 编译core（瘦身后）
cd common/core && mvn clean install -DskipTests

# 3. 编译依赖模块
cd common/data && mvn clean compile -DskipTests
cd common/web && mvn clean compile -DskipTests

# 4. 编译业务模块
cd system/service && mvn clean compile -DskipTests
```

**预期结果**: ✅ 所有模块编译成功

---

### 依赖树验证

```bash
# 验证core不再有Spring依赖
cd common/core && mvn dependency:tree | grep spring
# 预期：无输出

# 验证security-api只有Lombok
cd common/security-api && mvn dependency:tree
# 预期：只有lombok

# 验证data正确依赖security-api
cd common/data && mvn dependency:tree | grep security-api
# 预期：com.frog.common:security-api
```

---

### 运行时验证

```bash
# 启动服务验证
cd system && mvn spring-boot:run
cd auth && mvn spring-boot:run
cd gateway && mvn spring-boot:run
```

**验证点**:
1. ✅ 服务启动成功
2. ✅ Spring能找到SecurityContext、PermissionService的实现
3. ✅ DataScope功能正常
4. ✅ 权限检查功能正常

---

## ⚠️ 潜在风险和缓解措施

### 风险1: 依赖找不到

**问题**: 其他模块可能依赖core中被移除的类

**缓解**:
1. ✅ 先分析依赖树，确认影响范围
2. ✅ 逐步移动，每次只移动一个接口
3. ✅ 编译验证每一步

---

### 风险2: 循环依赖

**问题**: 新模块可能与现有模块形成循环依赖

**缓解**:
1. ✅ security-api只依赖JDK和Lombok（无循环可能）
2. ✅ 保持依赖单向：core ← security-api ← data/web

---

### 风险3: 测试失败

**问题**: 移动代码后测试可能失败

**缓解**:
1. ✅ 先运行现有测试（阶段1和阶段2的测试）
2. ✅ 如果测试失败，只需更新import路径
3. ✅ 测试逻辑不需要修改

---

## 🔄 回滚计划

如果重构出现问题，可以快速回滚：

### 回滚步骤

1. 删除 common/security-api 模块
2. 将 SecurityContext.java 和 PermissionService.java 移回 common/core
3. 恢复 common/core/pom.xml 的原始依赖
4. 恢复其他模块pom.xml的原始依赖
5. 恢复父pom.xml的modules配置

### 回滚命令

```bash
# 快速回滚（如果保留了git历史）
git checkout common/core/pom.xml
git checkout common/data/pom.xml
git checkout common/web/pom.xml
rm -rf common/security-api
```

---

## 📋 实施检查清单

### 准备阶段
- [ ] 创建feature分支：`git checkout -b refactor/phase3-core-splitting`
- [ ] 备份当前状态：`git commit -am "Backup before phase3"`
- [ ] 确认阶段1和阶段2测试通过

### 实施阶段
- [ ] 创建 common/security-api 目录结构
- [ ] 创建 security-api/pom.xml
- [ ] 移动 SecurityContext.java
- [ ] 移动 PermissionService.java
- [ ] 更新 common/core/pom.xml（移除Spring依赖）
- [ ] 更新 common/data/pom.xml（添加security-api依赖）
- [ ] 更新 common/web/pom.xml（添加security-api依赖）
- [ ] 更新 system/service/pom.xml（添加security-api依赖）
- [ ] 更新父pom.xml的modules
- [ ] 更新父pom.xml的dependencyManagement

### 验证阶段
- [ ] 编译 common/security-api
- [ ] 编译 common/core（瘦身后）
- [ ] 编译 common/data
- [ ] 编译 common/web
- [ ] 编译 system/service
- [ ] 运行测试：DataScopeAspectTest
- [ ] 运行测试：DataScopeInterceptorTest
- [ ] 启动system服务验证
- [ ] 启动auth服务验证

### 完成阶段
- [ ] 生成重构报告：REFACTORING_PHASE3_COMPLETE.md
- [ ] 更新总结文档：REFACTORING_COMPLETE_SUMMARY.md
- [ ] 提交代码：`git commit -am "refactor(phase3): split common/core into security-api"`
- [ ] 创建PR或合并到主分支

---

## 🎯 成功标准

### 必须满足

1. ✅ common/security-api 模块成功创建
2. ✅ common/core 不再依赖Spring（只有Lombok+Jackson+Guava）
3. ✅ 所有模块编译成功
4. ✅ 所有测试通过（阶段1和阶段2的测试）
5. ✅ 服务能正常启动和运行

### 期望达到

6. ✅ 启动时间减少 10-15%
7. ✅ core模块依赖减少 80%
8. ✅ 依赖树清晰，无循环依赖
9. ✅ 文档完整，包含重构报告

---

## 📚 参考资料

- [Maven多模块项目](https://maven.apache.org/guides/mini/guide-multiple-modules.html)
- [Spring模块化设计](https://docs.spring.io/spring-framework/reference/)
- [依赖倒置原则（DIP）](https://en.wikipedia.org/wiki/Dependency_inversion_principle)

---

**计划制定时间**: 2025-12-12
**预计执行时间**: 2-3天
**风险等级**: 中等（有回滚方案）
**优先级**: 中（可选优化）