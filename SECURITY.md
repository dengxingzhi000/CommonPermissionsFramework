# 安全政策

## 支持的版本

当前支持的版本及其安全更新政策如下：

| 版本 | 发布日期 | 支持截止日期 | 安全补丁 |
|------|----------|--------------|----------|
| 1.2.x | 2025-12-11 | 2027-06-11 | ✅ 活跃 |
| 1.1.x | 2025-10-20 | 2026-10-20 | ✅ 仅关键补丁 |
| 1.0.x | 2025-09-26 | 2026-09-26 | ❌ 不支持 |

## 报告安全漏洞

**重要**: 请不要通过公开的 GitHub Issues 报告安全漏洞。

如果您发现了安全漏洞，请通过以下方式报告：

### 报告方式

1. **邮件报告**（推荐）
   - 发送至: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
   - 主题: `[SECURITY] <Your Issue Title>`

2. **GitHub 私密报告**（如果可用）
   - 访问项目的 Security Advisory 页面
   - 点击 "Report a vulnerability"

### 包含信息

请在报告中包含以下信息：

- **漏洞描述**: 清楚地描述安全问题
- **影响范围**: 哪些版本受到影响
- **复现步骤**: 如何重现该问题（如果适用）
- **潜在后果**: 这个漏洞可能导致什么
- **建议修复**: 如果有的话，您的修复建议
- **联系方式**: 您的邮箱和名字

### 报告示例

```
主题: [SECURITY] SQL 注入漏洞在 UserService

漏洞描述:
在 UserService.searchUsers() 方法中存在 SQL 注入漏洞。
用户输入直接拼接到 SQL 查询中，没有使用参数化查询。

影响版本:
- v1.2.0
- v1.1.0
- v1.0.0

复现步骤:
1. 访问用户搜索端点: /system/users/search
2. 在 searchTerm 参数中输入: ' OR '1'='1
3. 系统返回所有用户而不仅仅是匹配的用户

潜在后果:
攻击者可以：
- 读取敏感用户数据
- 修改或删除数据库记录
- 获得数据库管理员权限

建议修复:
使用 MyBatis-Plus 的参数化查询或 QueryWrapper。
```

## 安全更新流程

### 漏洞确认

1. 我们会在 24 小时内确认收到报告
2. 我们会评估漏洞的严重性和影响范围
3. 我们会通知报告者我们的初步评估

### 修复和发布

1. 我们会为确认的漏洞创建修复
2. 关键漏洞会在 7 天内发布补丁
3. 重要漏洞会在 30 天内发布补丁
4. 中等漏洞会在下一个定期发布中修复

### 披露

1. 在补丁发布后，我们会公开漏洞信息
2. 我们会给予报告者适当的认可（如果他们同意）
3. 我们会发布安全公告和补丁说明

## 安全最佳实践

### 部署安全性

1. **使用 HTTPS**
   - 所有通信都应该通过 HTTPS 进行
   - 配置适当的 SSL/TLS 证书

2. **API 认证**
   - 使用强的 JWT 密钥（至少 256 位）
   - 定期轮换密钥
   - 使用安全的令牌存储

3. **数据库安全**
   - 使用强密码
   - 限制数据库访问
   - 启用加密存储
   - 定期备份

4. **服务间通信**
   - 启用 mTLS（双向 TLS）
   - 验证服务证书
   - 使用安全的 RPC 协议

### 配置安全性

```yaml
# application.yaml - 安全配置示例

spring:
  security:
    jwt:
      secret: ${JWT_SECRET}  # 从环境变量读取，不要硬编码
      expiration: 3600000    # 1 小时

  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}  # 使用环境变量

# IP 访问控制
gateway:
  ip-access-control:
    enabled: true
    whitelist-only: false

# API 签名验证
gateway:
  api-signature:
    enabled: true
    algorithm: HMAC_SHA256
```

### 依赖安全

1. **定期更新依赖**
   ```bash
   # 检查已知漏洞
   mvn dependency-check:check

   # 更新依赖
   mvn versions:display-dependency-updates
   ```

2. **审查依赖**
   - 只添加必要的依赖
   - 优先使用官方和可信的库
   - 检查库的安全历史

3. **监控漏洞**
   - 订阅安全公告
   - 定期运行漏洞扫描
   - 实施 SBOM（软件物料清单）

### 代码安全

1. **输入验证**
   ```java
   // ❌ 不安全
   List<User> users = userRepository.findByName(searchTerm);

   // ✅ 安全
   String safeTerm = sanitizeInput(searchTerm);
   List<User> users = userRepository.findByName(SafeSearchTerm.of(safeTerm));
   ```

2. **避免硬编码密钥**
   ```java
   // ❌ 不安全
   private static final String SECRET = "my-secret-key";

   // ✅ 安全
   @Value("${app.secret-key}")
   private String secretKey;
   ```

3. **安全的密码存储**
   ```java
   // ✅ 使用 bcrypt 或 argon2
   password = passwordEncoder.encode(plainPassword);
   ```

4. **避免 OWASP Top 10 漏洞**
   - SQL 注入
   - 认证突破
   - 敏感数据泄露
   - XML 外部实体攻击（XXE）
   - 破碎的访问控制
   - 安全配置错误
   - XSS 跨站脚本
   - 不安全的反序列化
   - 使用已知存在漏洞的组件
   - 日志和监控不足

## 安全审计

定期进行安全审计：

1. **代码审查**
   - 所有代码变更都需要审查
   - 关注安全问题

2. **静态分析**
   ```bash
   mvn spotbugs:check
   mvn checkstyle:check
   ```

3. **动态分析**
   - 进行渗透测试
   - 测试 API 安全
   - 测试认证和授权

4. **依赖审计**
   - 检查已知漏洞
   - 监控新的安全警告

## 安全资源

### 文档
- [Spring Security 文档](https://spring.io/projects/spring-security)
- [OWASP Top 10](https://owasp.org/Top10/)
- [Java 安全编码指南](https://wiki.sei.cmu.edu/confluence/display/java/)

### 工具
- [OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/)
- [SpotBugs](https://spotbugs.readthedocs.io/)
- [Checkstyle](https://checkstyle.sourceforge.io/)

### 咨询
- [NIST 网络安全框架](https://www.nist.gov/cyberframework)
- [CWE/SANS Top 25](https://cwe.mitre.org/top25/)

## 安全声明

- 本项目承诺遵循行业最佳实践
- 我们重视安全研究者的工作
- 我们致力于及时修复安全问题
- 我们欢迎负责任的漏洞披露

## 版本历史和补丁

### v1.2.0（2025-12-11）
- ✅ 强化 IP 访问控制
- ✅ 改进身份认证传播
- ✅ 增强 API 签名验证
- ✅ 改进数据权限控制

### v1.1.0（2025-10-20）
- ✅ WebAuthn 多因素认证
- ✅ 安全框架配置完善

### v1.0.0（2025-09-26）
- 首次发布，包含完整的安全框架

---

感谢所有帮助我们保持项目安全的安全研究者和用户！
