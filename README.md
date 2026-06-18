# CommonPermissionsFramework - 微服务权限框架

<div align="center">

[![GitHub Stars](https://img.shields.io/github/stars/dengxingzhi000/CommonPermissionsFramework?style=flat-square&color=green)](https://github.com/dengxingzhi000/CommonPermissionsFramework)
[![GitHub Forks](https://img.shields.io/github/forks/dengxingzhi000/CommonPermissionsFramework?style=flat-square&color=blue)](https://github.com/dengxingzhi000/CommonPermissionsFramework)
[![License](https://img.shields.io/badge/license-Apache%202.0-red?style=flat-square)](LICENSE)
[![Java Version](https://img.shields.io/badge/Java-21-brightgreen?style=flat-square)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-6DB33F?style=flat-square)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.0-orange?style=flat-square)](https://spring.io/projects/spring-cloud)

基于 Spring Boot 4 + Spring Cloud 2025 + Spring Security + JWT 的企业级微服务权限管理框架。

[简介](#简介) • [核心功能](#核心功能) • [技术栈](#技术栈) • [快速开始](#快速开始) • [项目结构](#项目结构) • [贡献指南](#贡献指南)

</div>

---

## 简介

**CommonPermissionsFramework** 是一个基于 Spring Boot 4 + Spring Cloud 2025 的企业级微服务权限管理框架。整合了 Spring Security、OAuth2、JWT、WebAuthn、Nacos、Dubbo 等生产级组件，提供开箱即用的权限管理解决方案。

### 核心优势

- **开箱即用** - 完整的权限管理框架，快速集成到业务系统
- **安全可靠** - 多层次安全防护：MFA、API 签名、WebAuthn、IP 控制、mTLS
- **组件齐全** - 用户、角色、部门、权限、审计等完整管理模块
- **高性能** - 分库分表、多级缓存、Redis SCAN 清理、Guava 有界缓存
- **云原生** - Docker + Kubernetes 部署，环境变量驱动配置
- **可观测性** - Prometheus、Grafana、SkyWalking、ELK 监控体系

---

## 核心功能

### 安全框架

- **身份认证**
  - JWT 令牌认证 + 刷新令牌机制
  - OAuth2.0 统一认证中心
  - WebAuthn 多因素认证（FIDO2）
  - MFA 两步验证（TOTP）

- **权限管理**
  - RBAC 角色权限管理
  - 动态权限加载
  - 数据权限控制（DataScope）
  - 资源级权限校验

- **网关安全**
  - IP 访问控制（Redis 动态黑名单/白名单）
  - API 签名验证（HMAC-SHA256 + Redis SETNX 防重放）
  - mTLS 服务间加密
  - 身份信息安全传播

### 用户管理
- 用户、角色、部门三级权限体系
- 用户登录、注册、密码重置
- 在线用户管理、会话控制
- 用户行为审计日志

### 消息集成
- Kafka + RabbitMQ 双引擎支持
- 可靠消息发布与订阅（受限信任包反序列化）
- 消息幂等性保证
- 事件驱动架构支持

### 数据持久化
- MyBatis-Plus 3.5 + PostgreSQL
- ShardingSphere 分库分表
- 读写分离（@Master/@Slave 注解路由）
- 多级缓存系统（本地 + Redis）

### 监控和追踪
- Prometheus + Grafana 指标监控
- Sentinel 流量控制和熔断降级
- SkyWalking 全链路追踪
- ELK 日志聚合分析

---

## 技术栈

### 核心框架
| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 4.0.0 | Web 框架 |
| Spring Cloud | 2025.1.0 | 微服务框架 |
| Spring Security | 6.x | 安全框架 |
| Spring Cloud Alibaba | 2025.0.0.0 | 阿里云生态 |

### 中间件
| 组件 | 用途 |
|------|------|
| Nacos | 配置中心 + 服务发现 |
| Dubbo | 高性能 RPC 框架 |
| OpenFeign | HTTP 客户端（降级备用） |
| Redis | 缓存 + 分布式锁 + 防重放 |
| Kafka / RabbitMQ | 消息队列 |
| PostgreSQL | 关系型数据库 |

### 可观测性
| 工具 | 功能 |
|------|------|
| Prometheus | 指标收集 |
| Grafana | 可视化监控 |
| SkyWalking | 全链路追踪 |
| Sentinel | 流量控制、熔断降级 |
| ELK | 日志聚合分析 |

---

## 快速开始

### 前置需求
- JDK 21 或更高版本
- Maven 3.8+
- PostgreSQL 15+
- Redis 7+
- Nacos 2.2+
- Docker 和 Docker Compose（可选）

### 环境变量

部署前必须设置以下环境变量：

```bash
# 数据库
DB_HOST=localhost
DB_PORT=5432
DB_NAME=permissions
DB_USERNAME=postgres
DB_PASSWORD=your_db_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=63379
REDIS_PASSWORD=your_redis_password

# JWT
JWT_SECRET=your_jwt_secret_at_least_256_bits

# Nacos
NACOS_SERVER=localhost:8848
NACOS_NAMESPACE=public
NACOS_GROUP=DEFAULT_GROUP

# 安全
API_SIGNING_SECRET=your_api_signing_secret
IDENTITY_TOKEN_SECRET=your_identity_token_secret
SENTINEL_DASHBOARD=localhost:8080
```

### 启动依赖服务

```bash
# Docker Compose 快速启动
docker-compose -f docker-compose.yml up -d

# 或手动启动
docker run -d --name nacos -e MODE=standalone -p 8848:8848 nacos/nacos-server:v2.2.0
docker run -d --name redis -p 63379:6379 redis:7
docker run -d --name postgres -p 5432:5432 -e POSTGRES_DB=permissions postgres:15
```

### 启动服务（顺序重要）

```bash
# 1. system-service（基础服务）
cd system/service
mvn spring-boot:run

# 2. auth-service（认证服务）
cd ../../auth
mvn spring-boot:run

# 3. gateway-service（API 网关）
cd ../gateway
mvn spring-boot:run
```

### 验证启动

```bash
# 查看已注册服务
curl http://localhost:8848/nacos/v1/ns/service/list

# 测试网关健康检查
curl http://localhost:9095/actuator/health

# 获取 JWT 令牌
curl -X POST http://localhost:9095/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

---

## 项目结构

```
CommonPermissionsFramework/
├── auth/                               # 认证服务
├── gateway/                            # API 网关
├── system/                             # 系统服务
│   ├── api/                            # Dubbo API 接口
│   └── service/                        # 服务实现
├── common/                             # 公共模块
│   ├── core/                           # 核心工具（无 Spring 依赖）
│   ├── security-api/                   # 安全接口定义
│   ├── data/                           # 数据持久化 + 缓存 + 读写分离
│   ├── web/                            # Web 安全 + Feign + UAA
│   │   └── securityCore/               # SecurityUser 等安全核心
│   ├── monitoring/                     # 监控 + Sentinel
│   └── integration/                    # Kafka/RabbitMQ 消息集成
├── config/                             # 配置模板
├── docs/                               # 文档
├── charts/                             # Helm Charts
├── scripts/                            # 部署脚本
└── Dockerfile                          # 多阶段构建
```

---

## 常见问题

### Q: 支持哪些数据库？
A: 主要支持 PostgreSQL 15+。MyBatis-Plus 支持的其他数据库（MySQL、Oracle 等）理论上也可以使用，需要修改 `DbType` 配置。

### Q: 可以用于生产环境吗？
A: 可以。框架经过架构审查，修复了 MFA 绕过、反序列化 RCE、硬编码密钥等安全问题。生产部署需确保所有敏感配置通过环境变量注入。

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
- Java 21，遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- 使用 Lombok 减少模板代码
- 所有公共方法需要 JavaDoc 注释
- 提交信息遵循 [约定式提交](https://www.conventionalcommits.org/zh-hans/)

---

## 更新日志

### v1.4.0（2026-06-18）- 架构审查修复
**安全修复（P0）**
- 修复 Token 刷新 MFA 绕过漏洞
- 修复 Kafka 反序列化 RCE（受限信任包）
- 移除所有硬编码密钥，改用环境变量
- 修复 WebAuthn 源站绕过（服务端 rpId）
- 修复 JWK URI 硬编码为 localhost

**可靠性修复（P1）**
- 修复 API 签名 nonce TOCTOU 竞态（Redis SETNX）
- 新增 ReadWriteRoutingCleanupFilter 防止 ThreadLocal 泄漏
- 修复 checkResourcePermission SQL 忽略参数
- SecurityUser 敏感字段添加 @JsonIgnore

**性能修复（P2）**
- BusinessMetrics 无界 Map 改为 Guava Cache（10K 上限）
- 修复 RoundRobinLoadBalancer 整数溢出
- TokenCleanupTask Redis KEYS 改为 SCAN
- GlobalExceptionHandler 从注释中恢复
- 修复 StripPrefix 双重应用

**构建修复**
- 修复 SentinelAutoConfiguration UTF-16 编码损坏
- 修复 Jackson 3.x 兼容性（Spring Kafka 4.x）
- 补全 Feign 客户端缺失方法

### v1.3.0（2025-12-16）
- 新增企业级读写分离框架
- 新增数据同步框架（Kafka + 幂等消费 + 重试）
- Spring Boot 4.0 兼容性优化

### v1.2.0（2025-12-11）
- 新增完整消息集成框架（Kafka/RabbitMQ）
- 强化 IP 访问控制和身份认证传播
- 优化数据权限控制系统

### v1.1.0（2025-10-20）
- 集成 WebAuthn 多因素认证
- 完善安全框架配置

### v1.0.0（2025-09-26）
- 首次正式发布

---

## 许可证

本项目采用 Apache License 2.0 许可证。详见 [LICENSE](LICENSE) 文件。

---

<div align="center">

**Made with ❤️ by [dengxingzhi](https://github.com/dengxingzhi000)**

如果项目对你有帮助，请给个 Star 支持一下！

</div>
