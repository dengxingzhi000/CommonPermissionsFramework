# CommonPermissionsFramework - 微服务权限框架

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/dengxingzhi000/CommonPermissionsFramework?style=flat-square&color=green)](https://github.com/dengxingzhi000/CommonPermissionsFramework)
[![GitHub Forks](https://img.shields.io/github/forks/dengxingzhi000/CommonPermissionsFramework?style=flat-square&color=blue)](https://github.com/dengxingzhi000/CommonPermissionsFramework)
[![License](https://img.shields.io/badge/license-Apache%202.0-red?style=flat-square)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-17%2B-brightgreen?style=flat-square)](https://www.oracle.com/java/)
[![SpringCloud Version](https://img.shields.io/badge/SpringCloud-2025.0.0-orange?style=flat-square)](https://spring.io/projects/spring-cloud)

基于 SpringCloud 2025 + Spring Security + JWT 的企业级微服务权限管理框架，开箱即用的一体化解决方案。

[简介](#简介) • [核心功能](#核心功能) • [技术栈](#技术栈) • [快速开始](#快速开始) • [最新特性](#最新特性) • [贡献指南](#贡献指南)

</div>

---

## 简介

**CommonPermissionsFramework** 是一个基于 SpringCloud 2025 的企业级微服务权限管理框架。整合了 Spring Security、JWT、Nacos、Dubbo 等生产级组件，提供开箱即用的权限管理解决方案。

### 🎯 核心优势

- ✨ **开箱即用** - 完整的权限管理框架，快速集成到业务系统
- 🔐 **安全可靠** - 多层次安全防护，包含 IP 访问控制、API 签名验证、mTLS 等
- 📦 **组件齐全** - 包含用户、角色、部门、权限、审计等完整管理模块
- 🚀 **高性能** - 支持分库分表、Redis 缓存、消息队列等优化
- ☁️ **云原生** - 完全支持 Docker、Kubernetes 部署
- 📊 **可观测性** - 集成 SkyWalking、Prometheus、ELK 等监控体系

---

## 核心功能

### 🔒 安全框架
- **身份认证**
  - JWT 令牌认证
  - OAuth2.0 统一认证中心
  - WebAuthn 多因素认证
  - 刷新令牌机制

- **权限管理**
  - RBAC 角色权限管理
  - 动态权限加载
  - 数据权限控制（DataScope）
  - 权限审批工作流

- **网关安全**
  - IP 访问控制（黑名单/白名单）
  - API 签名验证（HMAC-SHA256）
  - mTLS 服务间加密
  - 身份信息安全传播

### 👥 用户管理
- 用户、角色、部门三级权限体系
- 用户登录、注册、密码重置
- 在线用户管理、会话控制
- 用户行为审计日志

### 📨 通知系统
- 邮件、短信、站内消息多渠道通知
- 通知审计和投递状态跟踪
- 通知模板管理

### 📊 监控和追踪
- SkyWalking 全链路追踪
- Sentinel 流量控制和熔断降级
- Prometheus + Grafana 指标监控
- ELK 日志聚合分析

### 💬 消息集成（v1.2.0 新增）
- 支持 Kafka 和 RabbitMQ 双引擎
- 可靠消息发布与订阅
- 消息幂等性保证
- 事件驱动架构支持

### 🗄️ 数据持久化
- MyBatis-Plus ORM
- ShardingSphere 分库分表
- 读写分离
- 多级缓存系统

---

## 技术栈

### 核心框架
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 编程语言 |
| SpringBoot | 3.x | Web 框架 |
| SpringCloud | 2025.0.0 | 微服务框架 |
| Spring Security | 6.x | 安全框架 |
| SpringCloud Alibaba | 2024.0.1.0 | 阿里云生态 |

### 中间件
| 组件 | 用途 |
|------|------|
| Nacos | 配置中心 + 服务发现 |
| Dubbo | 高性能 RPC 框架 |
| OpenFeign | HTTP 客户端 |
| Redis | 缓存 + 分布式锁 |
| Kafka / RabbitMQ | 消息队列 |
| MySQL | 关系型数据库 |

### 可观测性
| 工具 | 功能 |
|------|------|
| SkyWalking | 全链路追踪 |
| Sentinel | 流量控制、熔断降级 |
| Prometheus | 指标收集 |
| Grafana | 可视化监控 |
| ELK | 日志聚合分析 |

---

## 快速开始

### 前置需求
- JDK 17 或更高版本
- Maven 3.8+
- Docker 和 Docker Compose（可选）
- Nacos 2.0+

### 环境配置

#### 1. 启动依赖服务

```bash
# 使用 Docker Compose 快速启动（推荐）
docker-compose -f docker-compose.yml up -d

# 或手动启动 Nacos
docker run -d --name nacos \
  -e MODE=standalone \
  -p 8848:8848 \
  nacos/nacos-server:v2.2.0
```

#### 2. 启动服务（启动顺序重要）

```bash
# 1. 启动 system-service（提供基础服务）
cd system
mvn spring-boot:run

# 2. 启动 auth-service（认证服务）
cd ../auth
mvn spring-boot:run

# 3. 启动 gateway-service（API 网关）
cd ../gateway
mvn spring-boot:run
```

### 验证启动成功

```bash
# 查看已注册的服务
curl http://localhost:8848/nacos/v1/ns/service/list

# 测试网关
curl http://localhost:9095/health

# 获取 JWT 令牌
curl -X POST http://localhost:9095/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

## 最新特性（v1.2.0）

### 🔐 安全框架增强
- **IP 访问控制强化** - 支持 Redis 存储的动态黑名单/白名单
- **身份认证传播优化** - 改进响应式安全上下文处理，支持 JWT 令牌验证
- **API 签名防篡改** - HMAC-SHA256 签名算法，防止请求被篡改

### 📨 消息集成框架（全新）
- **双引擎支持** - 同时支持 Kafka 和 RabbitMQ
- **可靠消息发布** - ReliableMessagePublisher 确保消息不丢失
- **消息幂等性** - IdempotencyChecker 防止重复处理
- **事件驱动** - 用户登录等核心事件自动发布

### 📊 数据权限增强
- **DataScope 优化** - 更高效的数据权限拦截
- **缓存策略改进** - 多级缓存管理系统

### ⚙️ 监控和限流
- **Sentinel 集成** - 动态限流规则刷新
- **SkyWalking 追踪** - 改进的链路追踪工具类

---

## 项目结构

```
CommonPermissionsFramework/
├── auth/                           # 认证服务
├── system/                         # 系统服务
├── gateway/                        # API 网关
├── common/                         # 公共模块
│   ├── data/                       # 数据持久化
│   ├── security/                   # 安全框架
│   ├── monitoring/                 # 监控和追踪
│   ├── log/                        # 审计日志
│   └── integration/                # 消息集成框架
├── config/                         # 配置文件
├── docs/                           # 文档
└── scripts/                        # 部署脚本
```

---

## 常见问题

### Q: 支持哪些数据库？
A: 目前主要支持 MySQL 8.0+。MyBatis-Plus 支持的其他数据库（PostgreSQL、Oracle 等）理论上也可以使用。

### Q: 可以用于生产环境吗？
A: 完全可以。本框架已在多个企业生产环境中验证，具备完整的安全、性能、监控体系。

### Q: 如何扩展权限系统？
A: 权限系统采用插件化设计，可以通过继承相关接口和覆盖 Bean 配置来扩展。

### Q: 如何禁用某些安全功能？
A: 所有安全功能都可以通过配置文件进行启用/禁用。详见各服务的 `application.yaml`。

---

## 贡献指南

我们欢迎任何形式的贡献！

### 提交流程
1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'feat: Add AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

### 代码规范
- 遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 所有公共方法需要 JavaDoc 注释
- 新功能需要编写单元测试（覆盖率 ≥ 80%）
- 提交信息遵循 [约定式提交](https://www.conventionalcommits.org/zh-hans/)

详见 [CONTRIBUTING.md](CONTRIBUTING.md)

---

## 相关链接

- 📚 [完整文档](docs/README.md)
- 🐛 [问题报告](https://github.com/dengxingzhi000/CommonPermissionsFramework/issues)
- 💬 [讨论](https://github.com/dengxingzhi000/CommonPermissionsFramework/discussions)
- 📋 [开发规范](common/integration/Developer.md)

---

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

---

## 更新日志

### v1.2.0（2025-12-11）✨ 最新版本
- 新增完整的消息集成框架（Kafka/RabbitMQ 支持）
- 强化 IP 访问控制和身份认证传播
- 优化数据权限控制系统
- 改进多级缓存管理
- 完善监控和追踪工具

### v1.1.0（2025-10-20）
- 集成 WebAuthn 多因素认证
- 完善安全框架配置
- 补充项目文档

### v1.0.0（2025-09-26）
- 首次正式发布
- 包含完整的权限管理框架
- 多层次安全防护

---

## 联系我们

有问题或建议？欢迎通过以下方式联系：

- 📧 Email: [dengxingzhi2015@gmail.com](mailto:dengxingzhi2015@gmail.com)
- 🐙 GitHub Issues: [提交问题](https://github.com/dengxingzhi000/CommonPermissionsFramework/issues)
- 💬 GitHub Discussions: [参与讨论](https://github.com/dengxingzhi000/CommonPermissionsFramework/discussions)

---

<div align="center">

**Made with ❤️ by [dengxingzhi](https://github.com/dengxingzhi000)**

如果项目对你有帮助，请给个 Star ⭐ 支持一下！

</div>
