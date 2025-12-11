# 贡献指南

感谢您对 CommonPermissionsFramework 的关注！我们欢迎各种形式的贡献，包括 Bug 报告、功能建议、文档改进和代码提交。

## 目录
- [行为准则](#行为准则)
- [贡献方式](#贡献方式)
- [开发流程](#开发流程)
- [代码规范](#代码规范)
- [提交规范](#提交规范)
- [拉取请求流程](#拉取请求流程)
- [开发环境设置](#开发环境设置)
- [测试指南](#测试指南)
- [许可证](#许可证)

---

## 行为准则

请参阅 [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) 了解我们社区的行为准则。

简而言之：
- 尊重所有贡献者
- 接受建设性的批评
- 关注于对项目最有益的内容
- 友善对待其他社区成员

---

## 贡献方式

### 🐛 报告 Bug

如果您发现了一个 Bug，请创建一个 GitHub Issue。请在 Issue 中包含：

1. **清晰的标题** - 简明扼要地描述问题
2. **复现步骤** - 具体说明如何重现 Bug
3. **预期行为** - 应该发生什么
4. **实际行为** - 实际发生了什么
5. **环境信息**:
   - 操作系统和版本
   - Java 版本
   - 项目版本
6. **错误日志** - 如果有错误堆栈跟踪，请包括在内
7. **代码示例** - 如果可能，提供最小化的代码示例

### 💡 提出功能建议

1. 使用 GitHub Discussions 或 Issues 来描述建议
2. 提供用例和预期的好处
3. 解释为什么这个功能对项目有价值
4. 列出可能的实现方式

### 📚 改进文档

文档改进总是受欢迎的！您可以：

1. 修复拼写或语法错误
2. 添加缺失的信息
3. 澄清不清楚的部分
4. 添加代码示例
5. 改进 API 文档

### 💻 代码贡献

代码贡献是项目最重要的！请遵循以下流程。

---

## 开发流程

### 1. Fork 项目

点击 GitHub 上的 Fork 按钮，在您的账户中创建项目副本。

### 2. Clone 您的 Fork

```bash
git clone https://github.com/YOUR_USERNAME/CommonPermissionsFramework.git
cd CommonPermissionsFramework
```

### 3. 添加上游远程库

```bash
git remote add upstream https://github.com/dengxingzhi000/CommonPermissionsFramework.git
```

### 4. 创建特性分支

```bash
# 更新 master
git fetch upstream
git checkout master
git merge upstream/master

# 创建特性分支
git checkout -b feature/your-feature-name
```

**分支命名规范:**
- 新功能: `feature/feature-name` 或 `feat/feature-name`
- Bug 修复: `fix/bug-name`
- 文档: `docs/doc-name`
- 重构: `refactor/module-name`
- 性能: `perf/optimization-name`
- 测试: `test/test-name`

### 5. 进行更改

编写代码并遵循代码规范（见下文）。

### 6. 测试您的更改

```bash
# 运行单元测试
mvn test

# 运行集成测试
mvn verify

# 检查代码质量
mvn checkstyle:check
mvn spotbugs:check
```

### 7. 提交更改

遵循提交规范（见下文）。

### 8. 推送到您的 Fork

```bash
git push origin feature/your-feature-name
```

### 9. 创建拉取请求

在 GitHub 上创建从您的分支到上游项目 `master` 分支的拉取请求。

---

## 代码规范

### Java 代码风格

本项目遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)。

**关键规则:**

1. **缩进**: 使用 4 个空格（不是制表符）
2. **列长**: 不超过 100 个字符
3. **命名约定**:
   - 类名: `PascalCase` - `UserService`
   - 方法名: `camelCase` - `getUserById`
   - 常量: `UPPER_SNAKE_CASE` - `MAX_RETRY_COUNT`
   - 包名: `lowercase` - `com.frog.system.service`

4. **类成员顺序**:
   ```
   1. 静态变量
   2. 实例变量
   3. 构造器
   4. 静态方法
   5. 实例方法
   6. 嵌套类
   ```

### 文档注释

所有公共类、方法和字段都必须有 JavaDoc 注释：

```java
/**
 * 用户服务实现类
 *
 * 提供用户的增删改查和权限管理功能。
 *
 * @author Your Name
 * @version 1.0
 * @since 2025-12-11
 */
public class UserServiceImpl implements IUserService {

    /**
     * 根据用户 ID 获取用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息，如果不存在则返回 null
     * @throws IllegalArgumentException 如果 userId 为 null
     */
    @Override
    public User getUserById(Long userId) {
        // ...
    }
}
```

### 异常处理

- 不要忽略异常
- 提供有意义的异常消息
- 使用具体的异常类型而不是通用的 `Exception`
- 记录异常

```java
// ❌ 不好
try {
    // 代码
} catch (Exception e) {
    // 忽略
}

// ✅ 好
try {
    // 代码
} catch (IOException e) {
    log.error("Failed to read file: {}", filePath, e);
    throw new FileReadException("Cannot read file: " + filePath, e);
}
```

### 代码设计原则

- **DRY** (Don't Repeat Yourself) - 避免重复代码
- **SOLID** 原则
- **可读性** - 代码应该易于理解
- **性能** - 考虑算法复杂度和资源使用
- **安全性** - 验证输入，避免 SQL 注入、XSS 等

---

## 提交规范

本项目使用 [约定式提交](https://www.conventionalcommits.org/zh-hans/)。

### 格式

```
<type>(<scope>): <subject>

[optional body]

[optional footer]
```

### 类型 (Type)

- `feat` ✨ - 新功能
- `fix` 🐛 - Bug 修复
- `docs` 📝 - 文档更新
- `style` 🎨 - 代码格式（不影响功能）
- `refactor` ♻️ - 代码重构
- `perf` ⚡ - 性能优化
- `test` ✅ - 测试相关
- `chore` 🔧 - 构建/配置/依赖更新

### 范围 (Scope)

使用模块或组件名：
- `gateway` - 网关模块
- `auth` - 认证模块
- `system` - 系统模块
- `common.security` - 安全框架
- `common.data` - 数据持久化
- `common.monitoring` - 监控
- `common.integration` - 消息集成

### 主题 (Subject)

- 使用命令式语气（"add" 而不是 "added" 或 "adds"）
- 不以大写字母开头
- 不以句号结尾
- 不超过 50 个字符

### 正文 (Body)

- 解释 **为什么** 做出这个改变
- 解释改变的**影响**
- 每行不超过 72 个字符

### 页脚 (Footer)

- `Closes #123` - 关闭相关 Issue
- `Refs #456` - 引用相关 Issue
- `BREAKING CHANGE:` - 标记破坏性更改

### 示例

```
feat(auth): add JWT token refresh mechanism

Implement automatic token refresh to improve user experience.
Tokens now automatically refresh when they're close to expiration.

This change includes:
- New RefreshTokenProvider interface
- JwtRefreshTokenFilter implementation
- Unit tests covering edge cases

Closes #42
```

---

## 拉取请求流程

### PR 检查清单

在提交 PR 之前，请确保：

- [ ] 代码遵循项目编码规范
- [ ] 添加了必要的注释和 JavaDoc
- [ ] 添加或更新了相关的测试
- [ ] 所有测试都通过
- [ ] 代码覆盖率不低于 80%
- [ ] 没有新增警告
- [ ] 文档已更新（如果需要）
- [ ] 提交信息遵循约定

### PR 标题和描述

**标题:**
- 简洁明了
- 与最后一个提交信息或 Issue 标题对应

**描述:**

```markdown
## 描述

简要说明这个 PR 的目的。

## 相关 Issue

Closes #123

## 变更类型

- [ ] Bug 修复
- [ ] 新功能
- [ ] 重大更改
- [ ] 文档更新

## 变更内容

详细列出具体的变更：
- 添加了什么
- 修复了什么
- 改进了什么

## 测试

说明如何测试这些变更：
- [ ] 单元测试
- [ ] 集成测试
- [ ] 手动测试

## 截图（如果适用）

添加相关的截图或日志。

## 其他

任何其他需要说明的内容。
```

### 代码审查

- 尊重审查者的建议
- 及时回复评论
- 进行必要的修改
- 推送新的提交而不是强制推送
- 让审查者确认修改后再合并

---

## 开发环境设置

### 前置要求

- JDK 17 或更高版本
- Maven 3.8 或更高版本
- Docker 和 Docker Compose（可选但推荐）
- Git

### 本地开发环境

#### 1. 克隆项目

```bash
git clone https://github.com/YOUR_USERNAME/CommonPermissionsFramework.git
cd CommonPermissionsFramework
```

#### 2. 启动依赖服务

```bash
# 使用 Docker Compose 启动所有依赖
docker-compose -f docker-compose.yml up -d

# 或单独启动 Nacos
docker run -d --name nacos \
  -e MODE=standalone \
  -p 8848:8848 \
  nacos/nacos-server:v2.2.0
```

#### 3. 构建项目

```bash
mvn clean install -DskipTests
```

#### 4. 运行测试

```bash
mvn test
```

#### 5. 启动服务

```bash
# 在不同的终端启动各个服务
# 终端 1: system-service
cd system && mvn spring-boot:run

# 终端 2: auth-service
cd ../auth && mvn spring-boot:run

# 终端 3: gateway-service
cd ../gateway && mvn spring-boot:run
```

### IDE 配置

**IntelliJ IDEA:**

1. 打开项目：File > Open > 选择项目根目录
2. 配置 Maven：File > Settings > Build > Maven > 设置 Maven 主目录
3. 配置代码风格：
   - File > Settings > Editor > Code Style > Java
   - Import Scheme > Google Style Guide 或手动配置为 4 空格缩进

**VS Code:**

1. 安装 Extension Pack for Java
2. 打开项目文件夹
3. 在 `.vscode/settings.json` 中配置：
   ```json
   {
     "[java]": {
       "editor.defaultFormatter": "redhat.java",
       "editor.formatOnSave": true,
       "editor.tabSize": 4
     }
   }
   ```

---

## 测试指南

### 单元测试

- 使用 JUnit 5
- 每个类都应该有对应的 `*Test` 类
- 测试覆盖率不低于 80%

```java
@DisplayName("UserService 测试")
class UserServiceTest {

    private UserService userService;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        userService = new UserServiceImpl(userRepository);
    }

    @Test
    @DisplayName("应该根据 ID 获取用户")
    void testGetUserById() {
        // Arrange
        Long userId = 1L;
        User expectedUser = new User(userId, "John");
        when(userRepository.findById(userId)).thenReturn(Optional.of(expectedUser));

        // Act
        User actualUser = userService.getUserById(userId);

        // Assert
        assertEquals(expectedUser, actualUser);
        verify(userRepository).findById(userId);
    }
}
```

### 集成测试

```java
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static GenericContainer<?> mysql = new GenericContainer<>("mysql:8.0")
        .withExposedPorts(3306)
        .withEnv("MYSQL_ROOT_PASSWORD", "root");

    @Autowired
    private UserService userService;

    @Test
    void testCreateAndRetrieveUser() {
        // 创建用户
        User user = new User("John", "john@example.com");
        User savedUser = userService.save(user);

        // 检索用户
        User retrievedUser = userService.getUserById(savedUser.getId());

        // 验证
        assertEquals(user.getName(), retrievedUser.getName());
    }
}
```

### 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=UserServiceTest

# 运行测试并生成覆盖率报告
mvn test jacoco:report
```

---

## 许可证

通过贡献代码，您同意您的贡献将在 Apache License 2.0 下许可。详见 [LICENSE](LICENSE) 文件。

---

## 联系方式

有问题？联系我们：

- 📧 Email: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
- 🐙 GitHub Issues: [提交问题](https://github.com/dengxingzhi000/CommonPermissionsFramework/issues)
- 💬 GitHub Discussions: [参与讨论](https://github.com/dengxingzhi000/CommonPermissionsFramework/discussions)

感谢您的贡献！🎉
