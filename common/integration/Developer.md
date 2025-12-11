# Integration 模块开发文档

## 概述

Integration 模块提供了基于 RabbitMQ 和 Kafka 的可靠消息传递能力，集成了 CloudEvents 规范、分布式追踪和可观测性功能。

---

## 核心特性

### 1. CloudEvents 消息封装

**MessageEnvelope** 遵循 CloudEvents 规范，包含以下字段：

- [id](file://D:\ProgramProject\NewNearSync\common\data\src\main\java\com\frog\common\dto\role\RoleDTO.java#L20-L20) - 消息唯一标识
- [type](file://D:\ProgramProject\NewNearSync\common\web\src\main\java\com\frog\common\security\annotation\Sensitive.java#L26-L26) - 事件类型
- [source](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\model\MessageEnvelope.java#L38-L38) - 事件源
- [specVersion](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\model\MessageEnvelope.java#L43-L43) - 规范版本
- [time](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\model\MessageEnvelope.java#L53-L53) - 事件时间戳
- [traceId](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\model\MessageEnvelope.java#L58-L58) - 分布式追踪 ID
- `tenant` - 租户信息
- [version](file://D:\ProgramProject\NewNearSync\gateway\src\main\java\com\frog\gateway\util\SignatureAlgorithm.java#L14-L14) - 消息版本
- 扩展字段支持

**文件路径**：`common/integration/src/main/java/com/frog/common/integration/model/MessageEnvelope.java`

### 2. RabbitMQ 自动配置

**RabbitIntegrationAutoConfiguration** 提供：

- ✅ Jackson JSON 消息转换器
- ✅ 强制持久化投递
- ✅ Publisher Confirm 和 Return 回调
- ✅ 预配置的 RabbitTemplate
- ✅ 默认幂等性实现
- ✅ 可靠消息发布器
- ✅ 带监控的消息消费器

**配置文件**：`common/integration/src/main/java/com/frog/common/integration/config/*`  
**自动装配**：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

### 3. 可靠消息发布器

**ReliableMessagePublisher** 支持：

| 功能 | 说明 |
|------|------|
| 同步确认 | 等待 broker 确认后返回 |
| 异步发送 | 非阻塞式发送 |
| 延迟消息 | 基于 `x-delay` header |
| 有序发送 | 基于 hashKey 分片保序 |
| 可观测性 | Micrometer Observation + OTel Span |

**文件路径**：`common/integration/src/main/java/com/frog/common/integration/messaging/ReliableMessagePublisher.java`

### 4. 消费器包装

**InstrumentedMessageConsumer** 提供：

- 🔍 分布式追踪（Tracing）
- 📊 指标采集（Metrics）
- 🔒 幂等性检查（默认内存版，可替换为 Redis/DB）

**文件路径**：
- `common/integration/src/main/java/com/frog/common/integration/messaging/InstrumentedMessageConsumer.java`
- `common/integration/src/main/java/com/frog/common/integration/idempotency/*`

### 5. Kafka 支持

**KafkaIntegrationAutoConfiguration** 提供：

- Producer/Consumer Factory
- 支持 Observation 的 KafkaTemplate
- KafkaMessagePublisher（带追踪和指标）
- InstrumentedKafkaConsumer

**配置文件**：
- `common/integration/src/main/java/com/frog/common/integration/config/KafkaIntegrationAutoConfiguration.java`
- `common/integration/src/main/java/com/frog/common/integration/config/KafkaIntegrationProperties.java`

**消息组件**：
- [KafkaMessagePublisher.java](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\messaging\KafkaMessagePublisher.java) - 发布器
- [InstrumentedKafkaConsumer.java](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\messaging\InstrumentedKafkaConsumer.java) - 消费包装器

---

## 示例代码

### 登录事件示例

完整的登录事件实现已迁移到新抽象，包含：

#### RabbitMQ 示例
- 事件定义：[UserLoginEvent](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\events\UserLoginEvent.java#L9-L21)
- 拓扑配置：队列、交换机绑定
- 生产者：使用 [ReliableMessagePublisher](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\messaging\ReliableMessagePublisher.java#L25-L102)
- 消费者：使用 [InstrumentedMessageConsumer](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\messaging\InstrumentedMessageConsumer.java#L18-L57)

#### Kafka 示例
- 事件定义：[UserLoginEvent](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\events\UserLoginEvent.java#L9-L21)
- Topic 配置：[UserLoginKafkaChannels](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\events\UserLoginKafkaChannels.java#L2-L5)
- 生产者：[UserLoginKafkaProducer.java](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\events\UserLoginKafkaProducer.java)
- 消费者：[UserLoginKafkaConsumer.java](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\events\UserLoginKafkaConsumer.java)（使用 `@KafkaListener`）

**文件路径**：`common/integration/src/main/java/com/frog/common/integration/events/*`

---

## 配置说明

### RabbitMQ 配置

配置前缀：`integration.messaging.*`

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `confirm-enabled` | 开启 Publisher Confirm | `true` |
| `x-delay-enabled` | 开启延迟消息 | `false` |
| `confirm-timeout` | 确认超时时间 | `5000ms` |
| `shard-count` | 有序发送分片数 | `4` |

**延迟消息注意事项**：
- ⚠️ 需要 RabbitMQ 安装 `x-delayed-message` 插件
- 或使用 TTL + DLX（Dead Letter Exchange）队列策略

### Kafka 配置

配置前缀：`integration.kafka.*`

| 配置项 | 说明 | 推荐值 |
|--------|------|--------|
| `bootstrap-servers` | Kafka 集群地址 | `localhost:9092` |
| `client-id` | 客户端 ID | 应用名称 |
| [acks](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\config\KafkaIntegrationProperties.java#L21-L21) | 确认级别 | `all` |
| [retries](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\config\KafkaIntegrationProperties.java#L25-L25) | 重试次数 | `3+` |
| `linger-ms` | 批量发送延迟 | `5` |
| `batch-size` | 批量大小 | `16384` |
| `max-in-flight` | 最大并发请求数 | `5` |
| [idempotence](file://D:\ProgramProject\NewNearSync\common\integration\src\main\java\com\frog\common\integration\config\KafkaIntegrationProperties.java#L41-L41) | 幂等性 | `true` |

---

## 使用要点

### 有序消息发送

Kafka 已补充 DLQ/重试监听能力，并提供配置模板。

- Kafka 配置增强：integration.kafka 增加 dlq-enabled、dlq-suffix、max-attempts、backoff-initial、backoff-max、backoff-multiplier。common/integration/src/main/java/com/frog/
  common/integration/config/KafkaIntegrationProperties.java
- 自动装配升级：KafkaIntegrationAutoConfiguration 现在注册 DefaultErrorHandler，使用 DeadLetterPublishingRecoverer 将失败消息投递至原 topic 加后缀（默认 .dlq），并按指数退避重
  试；listener 工厂默认挂载该错误处理器。common/integration/src/main/java/com/frog/common/integration/config/KafkaIntegrationAutoConfiguration.java
- Kafka 示例保持可用：UserLoginKafkaProducer/Consumer 仍使用 envelope + 观测封装，失败会按上述策略进入 DLQ。common/integration/src/main/java/com/frog/common/integration/events/*
- 配置模板：新增 config/templates/integration-messaging.yaml 覆盖 Rabbit 与 Kafka 的关键属性，便于后续落盘配置。
- README 已更新 Kafka DLQ/重试配置说明和模板位置。common/integration/README.md

说明：

- DLQ 逻辑：重试（含首次）达到 integration.kafka.max-attempts 后，将消息写入 <topic><dlq-suffix>，分区与原分区一致，保留 key/headers（包含 envelope 元数据）。
- 默认退避：初始 200ms，乘数 2，最大 5s，可通过上述属性调优。
- Kafka 自动装配仍启用 Observation/OTel，DLQ 与重试流程会打点。

