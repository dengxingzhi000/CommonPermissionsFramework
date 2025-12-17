# 读写分离配置指南

本文档介绍如何配置和使用企业级读写分离功能。

## 功能特性

- **自动路由**: 根据注解和事务类型自动路由到主/从库
- **负载均衡**: 支持轮询、加权轮询、随机、加权随机、最少连接等策略
- **健康检查**: 自动检测从库健康状态，支持复制延迟监控
- **故障转移**: 自动摘除不可用节点，支持降级到主库
- **读写一致性**: 写后读自动路由主库，避免复制延迟导致的数据不一致
- **监控指标**: 集成 Micrometer，提供完整的 Prometheus 指标
- **运维端点**: Actuator 端点支持运行时管理

## 架构设计

```
┌──────────────────────────────────────────────────────────────┐
│                        Application                            │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐         │
│  │   @Master   │   │   @Slave    │   │@Transactional│         │
│  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘         │
│         └────────────┬────┴────────────────┬─────────────────│
│                      ▼                                        │
│         ┌────────────────────────────────┐                   │
│         │   ReadWriteRoutingAspect       │                   │
│         │   (AOP 切面，设置路由上下文)     │                   │
│         └──────────────┬─────────────────┘                   │
│                        ▼                                      │
│         ┌────────────────────────────────┐                   │
│         │  ReadWriteRoutingContext       │                   │
│         │  (ThreadLocal 路由状态)         │                   │
│         └──────────────┬─────────────────┘                   │
│                        ▼                                      │
│         ┌────────────────────────────────┐                   │
│         │  ReadWriteRoutingDataSource    │                   │
│         │  (动态数据源路由)               │                   │
│         └──────────────┬─────────────────┘                   │
│                        │                                      │
│         ┌──────────────┼──────────────┐                      │
│         ▼              ▼              ▼                      │
│   ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│   │  Master  │  │  Slave1  │  │  Slave2  │                  │
│   │ (HikariCP)│ │(HikariCP) │ │(HikariCP) │                  │
│   └──────────┘  └──────────┘  └──────────┘                  │
└──────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.frog</groupId>
    <artifactId>common-data</artifactId>
    <version>${project.version}</version>
</dependency>
```

### 2. 配置 YAML

```yaml
spring:
  datasource:
    rw:
      enabled: true

      # 全局负载均衡策略
      load-balance: WEIGHTED_ROUND_ROBIN

      # 复制延迟容忍（超过此值强制走主库）
      replication-lag-tolerance: 1s

      # 写后读主库持续时间（解决读写一致性）
      read-master-after-write: 2s

      # 健康检查配置
      health-check-enabled: true
      health-check-interval: 30s
      failure-threshold: 3

      # 数据源组配置
      groups:
        # 用户库
        user:
          master:
            url: jdbc:postgresql://master.db:5432/db_user
            username: app_user
            password: ${DB_PASSWORD}
            minimum-idle: 5
            maximum-pool-size: 20
          slaves:
            - name: slave1
              url: jdbc:postgresql://slave1.db:5432/db_user
              username: app_user
              password: ${DB_PASSWORD}
              weight: 2
            - name: slave2
              url: jdbc:postgresql://slave2.db:5432/db_user
              username: app_user
              password: ${DB_PASSWORD}
              weight: 1

        # 权限库
        permission:
          master:
            url: jdbc:postgresql://master.db:5432/db_permission
            username: app_user
            password: ${DB_PASSWORD}
          slaves:
            - name: slave1
              url: jdbc:postgresql://slave1.db:5432/db_permission
              username: app_user
              password: ${DB_PASSWORD}
```

## 使用方式

### 注解方式

#### @Master - 强制走主库

适用于需要读取最新数据的场景：

```java
@Service
public class UserService {

    @Master(reason = "需要读取刚创建的用户")
    public SysUser getUserAfterCreate(Long userId) {
        return userMapper.selectById(userId);
    }

    @Master(reason = "关键业务查询，不能有延迟")
    public SysUser getCriticalUserInfo(Long userId) {
        return userMapper.selectById(userId);
    }
}
```

#### @Slave - 强制走从库

适用于可以接受延迟的查询场景：

```java
@Service
public class ReportService {

    @Slave  // 使用负载均衡选择从库
    public List<ReportData> generateReport() {
        return reportMapper.selectReportData();
    }

    @Slave("slave1")  // 指定特定从库
    public List<StatData> getStatistics() {
        return statMapper.selectStatistics();
    }

    @Slave(fallbackToMaster = false)  // 从库不可用时抛出异常
    public List<HistoryData> getHistory() {
        return historyMapper.selectHistory();
    }
}
```

#### 类级别注解

```java
@Service
@Slave  // 该类所有方法默认走从库
public class ReadOnlyReportService {

    public List<Report> listReports() {
        // 走从库
    }

    @Master(reason = "需要最新数据")  // 方法级覆盖类级
    public Report getLatestReport() {
        // 走主库
    }
}
```

### 事务自动路由

框架会自动识别 `@Transactional` 注解：

```java
@Service
public class UserService {

    @Transactional(readOnly = true)  // 自动路由到从库
    public List<SysUser> listUsers() {
        return userMapper.selectList();
    }

    @Transactional  // 写事务，自动路由到主库
    public void updateUser(SysUser user) {
        userMapper.updateById(user);
    }
}
```

### 编程式路由

```java
@Service
public class ComplexService {

    public void complexOperation() {
        // 强制后续操作走主库
        ReadWriteRoutingContext.forceMaster();
        try {
            // 所有数据库操作都走主库
            doSomething();
        } finally {
            ReadWriteRoutingContext.clearForceMaster();
        }
    }

    public void nestedOperation() {
        // 嵌套路由（使用栈结构）
        ReadWriteRoutingContext.push(RoutingType.MASTER);
        try {
            // 走主库
            doMasterOperation();

            ReadWriteRoutingContext.push(RoutingType.SLAVE);
            try {
                // 走从库
                doSlaveOperation();
            } finally {
                ReadWriteRoutingContext.pop();
            }

            // 恢复走主库
            doAnotherMasterOperation();
        } finally {
            ReadWriteRoutingContext.pop();
        }
    }
}
```

## 负载均衡策略

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| `ROUND_ROBIN` | 轮询 | 从库配置相同 |
| `WEIGHTED_ROUND_ROBIN` | 加权轮询（Nginx 同款算法） | 从库配置不同（推荐） |
| `RANDOM` | 随机 | 简单场景 |
| `WEIGHTED_RANDOM` | 加权随机 | 从库配置不同 |
| `LEAST_CONNECTIONS` | 最少连接 | 长连接场景 |

### 配置示例

```yaml
spring:
  datasource:
    rw:
      # 全局默认策略
      load-balance: WEIGHTED_ROUND_ROBIN

      groups:
        user:
          # 该组使用特定策略（覆盖全局）
          load-balance: LEAST_CONNECTIONS
          slaves:
            - name: slave1
              weight: 3  # 权重（加权策略使用）
            - name: slave2
              weight: 1
```

## 健康检查

### 检查内容

1. **连接有效性**: 使用 `Connection.isValid(5)` 检测
2. **复制延迟**: 查询 PostgreSQL `pg_last_wal_receive_lsn()` 检测延迟
3. **连续失败**: 超过阈值后自动摘除节点

### 配置项

```yaml
spring:
  datasource:
    rw:
      health-check-enabled: true    # 启用健康检查
      health-check-interval: 30s    # 检查间隔
      failure-threshold: 3          # 连续失败阈值
      replication-lag-tolerance: 1s # 复制延迟容忍
```

### 自动恢复

当从库恢复健康后，会自动重新加入负载均衡池。

## 监控指标

### Prometheus 指标

```
# 路由统计
datasource_rw_route_total{group="user",target="master"} 1234
datasource_rw_route_total{group="user",target="slave"} 5678

# 降级统计
datasource_rw_fallback_total{group="user"} 12

# 复制延迟
datasource_rw_replication_lag{group="user",slave="slave1"} 150
```

### Actuator 端点

```bash
# 查看所有状态
GET /actuator/readwrite

# 查看指定组状态
GET /actuator/readwrite/user

# 手动摘除从库
POST /actuator/readwrite -d '{"groupName":"user","slaveName":"slave1","action":"markUnavailable"}'

# 手动恢复从库
POST /actuator/readwrite -d '{"groupName":"user","slaveName":"slave1","action":"markAvailable"}'
```

### 健康检查端点

```bash
GET /actuator/health/readwrite

# 响应示例
{
  "status": "UP",
  "details": {
    "user.slave1": {
      "available": true,
      "replicationLagMs": 50,
      "consecutiveFailures": 0
    },
    "user.slave2": {
      "available": true,
      "replicationLagMs": 120,
      "consecutiveFailures": 0
    }
  }
}
```

## 读写一致性保证

### 写后读问题

在主从复制环境中，写入主库后立即读取可能读到从库的旧数据。

### 解决方案

框架通过 `read-master-after-write` 配置自动处理：

```yaml
spring:
  datasource:
    rw:
      read-master-after-write: 2s  # 写后 2 秒内读操作走主库
```

**原理**：
1. 写事务成功后，记录写入时间到 `ThreadLocal`
2. 后续读操作检查距离上次写入的时间
3. 在时间窗口内，自动路由到主库
4. 超过时间窗口后，恢复正常路由

### 手动标记

```java
@Service
public class OrderService {

    @Transactional
    public void createOrder(Order order) {
        orderMapper.insert(order);
        // 手动标记写操作（如果不使用 @Transactional）
        ReadWriteRoutingContext.markWrite();
    }

    public Order getOrder(Long orderId) {
        // 如果在 createOrder 后立即调用，会自动走主库
        return orderMapper.selectById(orderId);
    }
}
```

## 故障转移

### 自动摘除

当从库连续失败达到阈值时，自动从负载均衡池中摘除：

```
[WARN] Slave [user.slave1] marked as UNAVAILABLE after 3 consecutive failures
```

### 降级到主库

所有从库不可用时，自动降级到主库：

```
[WARN] Group [user] no available slave, fallback to MASTER
```

### 手动干预

通过 Actuator 端点进行手动操作：

```bash
# 紧急摘除从库
curl -X POST http://localhost:8080/actuator/readwrite \
  -H "Content-Type: application/json" \
  -d '{"groupName":"user","slaveName":"slave1"}'

# 恢复从库
curl -X POST http://localhost:8080/actuator/readwrite \
  -H "Content-Type: application/json" \
  -d '{"groupName":"user","slaveName":"slave1","available":true}'
```

## 多数据源组

支持配置多个数据源组，适用于微服务分库场景：

```yaml
spring:
  datasource:
    rw:
      groups:
        user:      # 用户库
          master: ...
          slaves: ...
        order:     # 订单库
          master: ...
          slaves: ...
        product:   # 商品库
          master: ...
          slaves: ...
```

### 获取指定组数据源

```java
@Service
public class MultiDbService {

    @Autowired
    private ReadWriteDataSourceProvider dataSourceProvider;

    public void customOperation() {
        DataSource userDs = dataSourceProvider.getDataSource("user");
        DataSource orderDs = dataSourceProvider.getDataSource("order");
        // 使用不同数据源...
    }
}
```

## 最佳实践

### 1. 合理设置权重

根据从库硬件配置设置权重：

```yaml
slaves:
  - name: slave1   # 高配服务器
    weight: 3
  - name: slave2   # 低配服务器
    weight: 1
```

### 2. 设置合适的读主时间窗口

根据业务复制延迟设置：

```yaml
# 复制延迟通常 < 100ms，设置 2s 比较安全
read-master-after-write: 2s
```

### 3. 监控复制延迟

配置告警规则：

```yaml
# Prometheus 告警规则
- alert: HighReplicationLag
  expr: datasource_rw_replication_lag > 1000
  for: 1m
  labels:
    severity: warning
  annotations:
    summary: "High replication lag detected"
```

### 4. 优雅处理从库不可用

```java
@Slave(fallbackToMaster = true)  // 默认降级到主库
public List<Data> queryData() {
    return mapper.selectList();
}

@Slave(fallbackToMaster = false)  // 从库不可用时抛出异常
public List<Report> criticalReport() {
    return reportMapper.selectReport();
}
```

### 5. 事务中的读操作

事务中的读操作应该走主库，保证数据一致性：

```java
@Transactional
public void transactionalOperation() {
    // 写操作
    userMapper.insert(user);

    // 事务中的读操作也走主库（@Transactional 自动处理）
    SysUser saved = userMapper.selectById(user.getId());
}
```

## 注意事项

1. **ThreadLocal 清理**: 框架自动管理，但在异步场景需要手动传递上下文
2. **连接池大小**: 主库需要处理写 + 读降级，连接池要留足余量
3. **复制延迟监控**: 建议配置 Prometheus 告警
4. **只读事务**: 使用 `@Transactional(readOnly = true)` 提高性能
5. **Spring Boot 版本**: 需要 Spring Boot 3.0+ / Java 17+

## 参考设计

本实现参考了业界成熟方案：

- [阿里巴巴 TDDL](https://github.com/alibaba/tb_tddl)
- [美团 Zebra](https://github.com/Meituan-Dianping/Zebra)
- [Apache ShardingSphere](https://shardingsphere.apache.org/)
- 字节跳动 ByteKV 读写分离模式
