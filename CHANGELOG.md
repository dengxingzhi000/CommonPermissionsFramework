# 更新日志

所有值得注意的项目变更都将在此文件中记录。

格式基于 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)，
本项目遵循 [语义化版本控制](https://semver.org/zh-CN/)。

## 未发布

### 计划中的功能
- OAuth2.0 社交登录集成（微信、QQ、GitHub）
- 基于时间的一次性密码（TOTP）支持
- 审批工作流增强
- GraphQL API 支持

---

## [1.2.0] - 2025-12-11

### 新增
- ✨ 完整的消息集成框架
  - Kafka 和 RabbitMQ 双引擎支持
  - ReliableMessagePublisher 确保消息不丢失
  - IdempotencyChecker 防止重复处理
  - 用户登录事件自动发布

- 🔐 安全框架增强
  - IP 访问控制强化（Redis 黑名单/白名单）
  - 身份认证传播优化（改进响应式安全上下文）
  - API 签名防篡改（HMAC-SHA256）
  - JWT 令牌类型显式检查

- 📊 数据权限控制优化
  - DataScope 拦截器强化
  - 缓存策略改进
  - 多级缓存管理系统

- ⚙️ 监控和追踪完善
  - Sentinel 限流规则动态刷新
  - SkyWalking 链路追踪改进
  - TraceUtils 业务追踪工具优化

- 📚 文档完善
  - 完整的项目 README 重写
  - 消息集成框架文档
  - 快速开始指南
  - 技术栈详解
  - 贡献指南 (CONTRIBUTING.md)
  - 行为准则 (CODE_OF_CONDUCT.md)
  - 安全政策 (SECURITY.md)

### 变更
- 优化 IpAccessControlFilter 的 Boolean 比较逻辑
- 改进 IdentityPropagationWebFilter 的响应式处理
- 增强通知系统的多渠道支持
- 改进 MyBatis-Plus 配置和性能

### 修复
- 修复数据权限拦截的边界情况
- 修复缓存失效的同步问题
- 修复 API 签名验证的字符编码问题

### 安全
- 改进输入验证和清理
- 增强敏感数据保护
- 更新依赖以修复已知漏洞

### 依赖更新
- Spring Cloud 2025.0.0
- Spring Boot 3.2.x
- Kafka Client 最新版本
- RabbitMQ 最新版本

---

## [1.1.0] - 2025-10-20

### 新增
- 🔐 WebAuthn 多因素认证支持
  - FIDO2 协议实现
  - 硬件密钥支持
  - 生物识别支持

- 📝 完善安全框架配置
  - 细粒度的安全策略配置
  - 动态权限加载机制
  - 权限缓存优化

- 📚 补充项目文档
  - API 文档增强
  - 架构设计文档
  - 部署指南

### 变更
- 重构安全配置体系
- 优化权限检查性能
- 改进错误处理和日志

### 修复
- 修复 JWT 令牌刷新的时间问题
- 修复权限缓存的一致性问题
- 修复并发场景下的竞态条件

---

## [1.0.0] - 2025-09-26

### 新增
- 🚀 首次正式发布

- 🔐 完整的安全框架
  - JWT 身份认证
  - RBAC 权限管理
  - API 签名验证
  - 数据权限控制

- 👥 用户管理系统
  - 用户、角色、部门三级权限体系
  - 用户登录、注册、密码重置
  - 在线用户管理、会话控制
  - 用户行为审计日志

- 📨 通知系统
  - 邮件通知
  - 短信通知
  - 站内消息通知
  - 通知模板管理

- 📊 监控和追踪
  - SkyWalking 全链路追踪
  - Sentinel 流量控制和熔断降级
  - Prometheus + Grafana 指标监控
  - ELK 日志聚合分析

- 🗄️ 数据持久化
  - MyBatis-Plus ORM
  - ShardingSphere 分库分表
  - 读写分离
  - Redis 缓存

- ☁️ 云原生支持
  - Docker 镜像
  - Kubernetes 部署配置
  - Helm Chart

---

## 发布政策

### 版本号

本项目遵循 [语义化版本控制](https://semver.org/zh-CN/)：

- **主版本号** (MAJOR) - 包含不兼容的 API 变更
- **次版本号** (MINOR) - 添加了向后兼容的新功能
- **修订版本号** (PATCH) - 进行了向后兼容的 Bug 修复

### 发布时间表

- **主版本** - 每 12 个月发布一次
- **次版本** - 每 3 个月发布一次
- **修订版本** - 根据需要发布

### 支持期限

| 版本 | 发布日期 | 支持截止 | 状态 |
|------|----------|----------|------|
| 1.2.x | 2025-12-11 | 2027-06-11 | 活跃 |
| 1.1.x | 2025-10-20 | 2026-10-20 | 安全补丁 |
| 1.0.x | 2025-09-26 | 2026-09-26 | 不支持 |

---

## 升级指南

### 从 1.1.x 升级到 1.2.0

1. **备份数据**
   ```bash
   # 备份数据库
   mysqldump -u root -p database > backup.sql
   ```

2. **更新依赖**
   ```bash
   # 更新 pom.xml
   mvn versions:update-properties
   ```

3. **数据库迁移**
   ```bash
   # 运行数据库脚本
   mysql -u root -p database < db-upgrade-v1.2.0.sql
   ```

4. **重启服务**
   ```bash
   # 重新构建和启动
   mvn clean package
   docker-compose up -d
   ```

### 从 1.0.x 升级到 1.1.0

请参阅 [升级指南](docs/upgrade-guide.md)

---

## 已知问题

- 在某些高并发场景下，缓存可能出现不一致
- WebAuthn 在某些旧浏览器中不完全支持

---

## 致谢

感谢所有为项目做出贡献的开发者和用户！

---

有问题？请在 [GitHub Issues](https://github.com/dengxingzhi000/CommonPermissionsFramework/issues) 中报告。
