# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CommonPermissionsFramework (NewNearSync) is an enterprise-grade microservices permission management framework built on Spring Cloud 2025, Spring Security 6, and Spring Boot 4. It provides multi-layered security with JWT authentication, RBAC authorization, data-level permissions, API signature validation, read-write separation, and event-driven architecture.

**Tech Stack:** Java 17+, Spring Boot 3.x/4.0, Spring Cloud 2025.0.0, Spring Cloud Alibaba 2024.0.1.0, MyBatis-Plus 3.5.14, Nacos, Dubbo, Redis, PostgreSQL/MySQL, Kafka/RabbitMQ

## Build & Run Commands

```bash
# Build all modules
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Build a specific module
cd auth && mvn clean package
```

### Running Services

**IMPORTANT: Services must be started in this order:**

```bash
# 1. Start system-service first (provides Dubbo services)
cd system && mvn spring-boot:run

# 2. Start auth-service (depends on system-service)
cd auth && mvn spring-boot:run

# 3. Start gateway (depends on both services)
cd gateway && mvn spring-boot:run
```

**Service Ports:**
- Gateway: 9095 (HTTP), 8761 (Admin)
- Auth Service: 8106 (HTTPS only)
- System Service: 8081 (HTTP)

### Testing

```bash
# Run all tests
mvn test

# Run tests for a specific module
cd common/web && mvn test

# Run a single test class
mvn test -Dtest=JwtUtilsTest

# Run a single test method
mvn test -Dtest=JwtUtilsTest#testGenerateToken

# Run with coverage report
mvn test jacoco:report

# Code quality checks
mvn checkstyle:check
mvn spotbugs:check
```

### Local Development

```bash
# Use Docker Compose for dependencies
docker-compose -f docker-compose.yml up -d

# Verify Nacos is running
curl http://localhost:8848/nacos

# Check registered services
curl http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10
```

## Architecture & Code Organization

### Module Structure

```
NewNearSync/
├── common/                          # Shared foundation modules
│   ├── core/                        # Core utilities, exceptions, PageResult, UUIDv7
│   ├── data/                        # MyBatis-Plus, caching, data scope, read-write separation
│   ├── web/                         # Security filters, JWT, Feign clients
│   │   └── securityCore/            # SecurityUser domain objects
│   ├── monitoring/                  # Sentinel circuit breaker
│   └── integration/                 # Kafka/RabbitMQ messaging
├── gateway/                         # Spring Cloud Gateway (entry point)
├── auth/                            # Authentication service
└── system/                          # Business domain services
    ├── api/                         # Dubbo service interfaces
    └── service/                     # Service implementations
```

**Dependency Flow:** Gateway/Auth/System → common/* modules (no upward dependencies)

### Key Architectural Patterns

#### 1. Multi-Layer Security Architecture

**Request Flow:**
```
Client Request
  ↓
[Gateway Layer]
  ├─ ApiSignatureFilter              # HMAC-SHA256 signature validation
  └─ IdentityPropagationWebFilter    # Adds signed identity headers
  ↓
[Service Layer Security Filters]
  ├─ SqlInjectionFilter              # SQL/XSS pattern detection
  ├─ JwtAuthenticationFilter         # JWT validation & context setup
  └─ StepUpFilter                    # MFA/WebAuthn enforcement
  ↓
[Method-Level Authorization]
  └─ @PreAuthorize / @Secured        # Spring Security annotations
```

**Important Security Components:**
- **JwtUtils** (common/web): Token generation/validation/blacklisting via Redis
- **SecurityConfig** (common/web): Filter chain configuration (order: SqlInjection → JWT → StepUp)
- **ApiSignatureFilter** (gateway): HMAC-SHA256 with nonce-based replay prevention
- **StepUpFilter** (common/web): Step-up authentication for sensitive operations

#### 2. Data Scope (Row-Level Security)

**Pattern:** Annotation-driven SQL rewriting for multi-tenant data isolation

```java
@DataScope(userAlias = "u", deptAlias = "d")
public List<SysUser> selectUserList(UserQuery query) {
    // MyBatis mapper call - SQL will be automatically rewritten
}
```

**How it works:**
1. `@DataScope` annotation triggers `DataScopeAspect`
2. Aspect populates `DataScopeContextHolder` (ThreadLocal)
3. `DataScopeInterceptor` (MyBatis interceptor) rewrites SQL
4. Adds WHERE clause based on user's data scope level

**Data Scope Levels:** ALL (1), CUSTOM (2), DEPT (3), DEPT_AND_CHILDREN (4), SELF (5)

#### 3. Read-Write Separation (v1.3.0)

**Pattern:** Annotation-driven automatic routing to master/slave databases

```java
@Service
public class UserService {

    @Master(reason = "需要读取最新数据")  // Force master
    public SysUser getUserAfterCreate(Long userId) {
        return userMapper.selectById(userId);
    }

    @Slave  // Use load-balanced slave
    public List<ReportData> generateReport() {
        return reportMapper.selectReportData();
    }

    @Transactional(readOnly = true)  // Auto-routes to slave
    public List<SysUser> listUsers() {
        return userMapper.selectList();
    }
}
```

**Features:**
- Load balancing strategies: ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, RANDOM, WEIGHTED_RANDOM, LEAST_CONNECTIONS
- Automatic health check with failover to master
- Write-after-read consistency (configurable window)
- Replication lag monitoring

**Configuration:** See `docs/read-write-separation.md` for full YAML configuration.

#### 4. Multi-Level Caching

**Cache Hierarchy:** L1 (Caffeine) → L2 (Redis) → Database

- `MultiLevelCache`: Coordinated L1+L2 with Redis pub/sub invalidation
- `TwoLevelCache`: Spring Cache abstraction wrapper (`@Cacheable`, `@CacheEvict`)

#### 5. Service-to-Service Communication

**Primary: Dubbo (High-Performance RPC)**
- Nacos registry, 3000ms timeout
- Providers in `system/service`, consumers in `auth` and `gateway`

**Fallback: Feign (REST HTTP)**
- Circuit breaker via Sentinel (50% exception ratio, 10s window)
- Fail-closed pattern for security

**Gateway Identity Propagation:**
- `IdentityPropagationWebFilter` adds signed headers: `X-Identity-Token`, `X-User-Id`, `X-User-Name`, `X-Device-Id`, `X-User-Roles`

#### 6. Event-Driven Architecture

**Dual Messaging Support:** Kafka and RabbitMQ

**Kafka:** Idempotence enabled, DLQ support, exponential backoff retry
**RabbitMQ:** sendSync/sendAsync/sendDelayed/sendOrderly modes, OpenTelemetry tracing

See `common/integration/Developer.md` for detailed messaging patterns.

## Common Development Patterns

### Adding a New Secured Endpoint

```java
@PreAuthorize("hasAuthority('system:user:list')")
@GetMapping("/users")
public ApiResponse<PageResult<SysUser>> listUsers(@Valid UserQuery query) {
    return ApiResponse.success(userService.selectUserList(query));
}
```

### Working with JWT Tokens

```java
// Token generation
String accessToken = JwtUtils.generateToken(userId, username, roles, permissions, deviceId, ipAddress);

// Token revocation
JwtUtils.blacklistToken(token);
JwtUtils.revokeUserTokens(userId);
```

### Handling Exceptions

All exceptions are handled by `GlobalExceptionHandler`:

```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
throw new AuthenticationException("Invalid credentials");
throw new RateLimitException("Too many requests");
```

## Configuration Management

### Environment Variables (Required)

```bash
JWT_SECRET=your-512-bit-secret-key-here-minimum-64-characters-required
AES_KEY=your-256-bit-aes-key-here
KEYSTORE_PASSWORD=your-keystore-password    # For auth service HTTPS
TRUSTSTORE_PASSWORD=your-truststore-password
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev
```

### Nacos Configuration Files

- `common.yaml` - Shared configuration (JWT, Redis, MyBatis)
- `{service-name}.yaml` - Service-specific configuration
- `{service-name}-{profile}.yaml` - Environment-specific overrides

## Important Implementation Notes

### Security Caveats
- JWT Secret must be 512-bit minimum (HMAC-SHA512)
- `SqlInjectionFilter` runs before authentication; tune patterns carefully
- Auth service requires HTTPS; configure `HttpsRedirectConfig`

### Data Scope Caveats
- `@DataScope` requires correct table aliases matching your SQL
- DEPT_AND_CHILDREN uses recursive CTE; may be slow on deep hierarchies
- Context is ThreadLocal; propagate manually for async operations

### Caching Pitfalls
- Always evict L1+L2 together via `MultiLevelCache.evict()`
- Ensure cached objects are serializable for Redis

### Message Publishing
- Always check message ID for idempotent processing
- Use `sendOrderly()` with partition key for strict ordering
- Monitor DLQ (default suffix: `.dlq`) for failed messages

## Database Conventions

- **Primary Keys:** UUIDv7 via `UUIDv7Util.generate()` (time-ordered)
- **Soft Deletes:** `deleted` column (0 = active, 1 = deleted) with `@TableLogic`
- **Audit Fields:** `createTime`, `createBy`, `updateTime`, `updateBy` (auto-populated via `MetaObjectHandler`)

## Monitoring & Troubleshooting

```bash
# Health checks
curl http://localhost:9095/actuator/health           # Gateway
curl -k https://localhost:8106/actuator/health       # Auth (HTTPS)
curl http://localhost:8081/actuator/health           # System

# Prometheus metrics
curl http://localhost:9095/actuator/prometheus

# Read-write separation status
curl http://localhost:8081/actuator/readwrite
```

### Common Issues

**Services fail to register with Nacos:** Check Nacos connectivity and `spring.cloud.nacos.discovery.server-addr`

**JWT validation fails:** Verify `JWT_SECRET` matches across services, check token blacklist in Redis

**Data scope not applying:** Ensure `@DataScope` is on service method (not controller), check `DataScopeContextHolder`

**Dubbo service not found:** Verify provider started before consumer, check Nacos registry

## Code Style & Conventions

- **Commits:** [Conventional Commits](https://www.conventionalcommits.org/) - `feat:`, `fix:`, `docs:`, `refactor:`, `perf:`, `test:`, `chore:`
- **Java Style:** Google Java Style Guide, 4 spaces indent
- **Lombok:** Prefer `@Data`, `@Builder`, `@Slf4j`
- **Response Format:** Always return `ApiResponse<T>` from controllers
- **Validation:** Use `@Valid` with JSR-303 annotations on DTOs