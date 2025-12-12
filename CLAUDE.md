# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CommonPermissionsFramework (NewNearSync) is an enterprise-grade microservices permission management framework built on Spring Cloud 2025, Spring Security 6, and Spring Boot 4. It provides multi-layered security with JWT authentication, RBAC authorization, data-level permissions, API signature validation, and event-driven architecture.

**Tech Stack:** Java 21, Spring Boot 4.0, Spring Cloud 2025.1.0, Spring Cloud Alibaba 2025.0.0.0, MyBatis-Plus 3.5.14, Nacos, Dubbo, Redis, PostgreSQL, Kafka/RabbitMQ

## Build & Run Commands

### Building the Project

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
cd system
mvn spring-boot:run

# 2. Start auth-service (depends on system-service)
cd auth
mvn spring-boot:run

# 3. Start gateway (depends on both services)
cd gateway
mvn spring-boot:run
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
```

### Local Development

```bash
# Use local profile (requires Docker Compose for dependencies)
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
│   ├── data/                        # MyBatis-Plus, caching, data scope
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
- **JwtUtils** (common/web): Token generation, validation, blacklisting
  - Secret: HMAC-SHA512 (512-bit minimum)
  - Claims: userId, username, roles, permissions, deviceId, ipAddress, jti, amr
  - Token revocation via Redis blacklist
  - Device binding and IP verification

- **SecurityConfig** (common/web): Filter chain configuration
  - Order matters: SqlInjection → JWT → StepUp
  - Each filter extends OncePerRequestFilter

- **ApiSignatureFilter** (gateway): Request signature validation
  - HMAC-SHA256 with nonce-based replay prevention
  - Redis-backed nonce storage (5-minute TTL)
  - Clock skew tolerance (configurable)

- **StepUpFilter** (common/web): Step-up authentication for sensitive operations
  - Checks policies based on path patterns, user lists, or roles
  - Circuit breaker for policy evaluation failures
  - Records security events via AuditLog

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

**Data Scope Levels:**
- Level 1: ALL - No filtering
- Level 2: CUSTOM - Database-driven custom rules
- Level 3: DEPT - Current department only
- Level 4: DEPT_AND_CHILDREN - Department tree (recursive CTE)
- Level 5: SELF - Current user only

**Key Files:**
- `DataScopeAspect.java` - AOP interceptor
- `DataScopeInterceptor.java` - MyBatis SQL rewriter
- `DataScopeContextHolder.java` - ThreadLocal context
- `DataScopeFilter.java` - Filter model

#### 3. Multi-Level Caching Strategy

**Cache Hierarchy:** L1 (Caffeine) → L2 (Redis) → Database

**Implementation:**
- `MultiLevelCache` - Coordinated L1+L2 cache
  - L1: Caffeine (10K entries max, with custom expiry policies)
  - L2: Redis (distributed, TTL-based)
  - Cross-instance invalidation via Redis pub/sub

- `TwoLevelCache` - Spring Cache abstraction wrapper
  - Integrates with `@Cacheable`, `@CacheEvict`, `@CachePut`

**Usage Pattern:**
```java
@Cacheable(value = "users", key = "#userId")
public SysUser getUserById(Long userId) {
    // Cache miss triggers DB query
}
```

#### 4. Service-to-Service Communication

**Dual RPC Strategy:**

**Primary: Dubbo (High-Performance RPC)**
- Used for: Permission lookups, user info fetches
- Nacos registry integration
- Consumer timeout: 3000ms
- Providers in `system/service`, consumers in `auth` and `gateway`

**Fallback: Feign (REST HTTP)**
- Circuit breaker via **Sentinel** (Alibaba Cloud ecosystem)
- Metrics tracking (success/fail counters)
- Automatic fallback on circuit open (fail-closed pattern)
- Rate limiting per resource

**Key Implementations:**
- `DubboPermissionAccess` / `FeignPermissionAccess` - Permission lookup
  - **Sentinel Resources:** `permission:findByUrl`, `permission:findByUserId`
  - Fail-closed: Throws `AccessDeniedException` when circuit is open
  - Configured degrade rules: 50% exception ratio, 10s time window
- `UserDubboService` - User info fetching
- Both implement same interfaces for seamless fallback

**Sentinel Integration:**
- Feign clients automatically wrapped by Sentinel
- Configuration in `common/web/src/main/resources/application.yaml`
- Degrade rules for circuit breaking based on exception ratio
- Flow rules for QPS limiting (100-200 req/s per resource)
- Security filters (`StepUpFilter`, `ApiAccessControlFilter`) use Sentinel SphU for protection

**Gateway Identity Propagation:**
- `IdentityPropagationWebFilter` adds signed headers:
  - `X-Identity-Token` (HMAC-signed JWT claims)
  - `X-User-Id`, `X-User-Name`, `X-Device-Id`, `X-User-Roles`

#### 5. Event-Driven Architecture (Message Integration)

**Dual Messaging Support:** Kafka and RabbitMQ

**Kafka Configuration** (`KafkaIntegrationAutoConfiguration`):
- Producer: Idempotence enabled, Jackson serialization
- Consumer: Dead-letter queue (DLQ) support
- Retry: Exponential backoff (configurable attempts)

**RabbitMQ Publisher** (`ReliableMessagePublisher`):
- Modes: sendSync, sendAsync, sendDelayed, sendOrderly
- OpenTelemetry tracing integration
- Publisher confirms with timeout
- Hash-based partitioning for ordering

**Usage Pattern:**
```java
@Autowired
private ReliableMessagePublisher publisher;

publisher.sendAsync("user.login", loginEvent, result -> {
    if (result.isSuccess()) {
        log.info("Event published successfully");
    }
});
```

## Common Development Patterns

### Adding a New Secured Endpoint

1. **Define the controller method:**
   ```java
   @PreAuthorize("hasAuthority('system:user:list')")
   @GetMapping("/users")
   public ApiResponse<PageResult<SysUser>> listUsers(@Valid UserQuery query) {
       return ApiResponse.success(userService.selectUserList(query));
   }
   ```

2. **Add permission to database** (if required):
   - Insert into `sys_permission` table with unique permission code
   - Assign to roles via `sys_role_permission`

3. **Apply data scope if needed:**
   ```java
   @DataScope(userAlias = "u", deptAlias = "d")
   public PageResult<SysUser> selectUserList(UserQuery query) {
       // Data scope automatically applied
   }
   ```

### Working with JWT Tokens

**Token Generation:**
```java
// In authentication flow
String accessToken = JwtUtils.generateToken(user.getUserId(), user.getUsername(),
    roles, permissions, deviceId, ipAddress);
String refreshToken = JwtUtils.generateRefreshToken(user.getUserId());

// Store token metadata
JwtUtils.storeTokenMetadata(userId, jti, deviceId, ipAddress);
```

**Token Validation:**
```java
// Automatic via JwtAuthenticationFilter, but manual validation:
if (JwtUtils.validateToken(token)) {
    Claims claims = JwtUtils.parseToken(token);
    Long userId = claims.get("userId", Long.class);
}
```

**Token Revocation:**
```java
// Blacklist a token
JwtUtils.blacklistToken(token);

// Revoke all user tokens
JwtUtils.revokeUserTokens(userId);
```

### Handling Exceptions

All exceptions are handled by `GlobalExceptionHandler` and `SecurityRestExceptionHandler`:

```java
// Throw custom exceptions
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
throw new AuthenticationException("Invalid credentials");
throw new RateLimitException("Too many requests");

// They automatically map to ApiResponse format:
{
  "code": 404,
  "message": "User not found",
  "data": null,
  "timestamp": 1702345678
}
```

### Adding Step-Up Authentication

Configure in `application.yaml`:
```yaml
security:
  step-up:
    enabled: true
    policies:
      - name: "Sensitive Operations"
        paths:
          - "/api/admin/**"
          - "/api/users/*/delete"
        required-amr: ["mfa", "webauthn"]
        users: ["admin", "superuser"]  # Optional: specific users
        roles: ["ROLE_ADMIN"]           # Optional: specific roles
```

The `StepUpFilter` will check JWT claims for `amr` (Authentication Methods Reference) and enforce MFA/WebAuthn.

### Using Message Publishing

**Publish an event:**
```java
@Autowired
private KafkaMessagePublisher kafkaPublisher;

UserLoginEvent event = UserLoginEvent.builder()
    .userId(user.getUserId())
    .loginTime(LocalDateTime.now())
    .ipAddress(request.getRemoteAddr())
    .build();

kafkaPublisher.sendAsync("user.login.topic", event);
```

**Consume an event:**
```java
@KafkaListener(topics = "user.login.topic", groupId = "audit-service")
public void handleUserLogin(UserLoginEvent event) {
    // Process login event (e.g., record audit log)
}
```

## Configuration Management

### Environment Variables (Required)

Set these in environment or `.env` file:

```bash
# JWT Security
JWT_SECRET=your-512-bit-secret-key-here-minimum-64-characters-required

# AES Encryption
AES_KEY=your-256-bit-aes-key-here

# HTTPS/mTLS (for auth service)
KEYSTORE_PASSWORD=your-keystore-password
TRUSTSTORE_PASSWORD=your-truststore-password

# Nacos Configuration
NACOS_SERVER_ADDR=localhost:8848
NACOS_NAMESPACE=dev
```

### Nacos Configuration Files

Each service loads configuration from Nacos:
- `common.yaml` - Shared configuration (JWT, Redis, MyBatis)
- `{service-name}.yaml` - Service-specific configuration
- `{service-name}-{profile}.yaml` - Environment-specific overrides

**Example Nacos config reference:**
```yaml
spring:
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_SERVER_ADDR}
        namespace: ${NACOS_NAMESPACE}
        file-extension: yaml
        shared-configs:
          - data-id: common.yaml
            refresh: true
```

## Important Implementation Notes

### Security Considerations

1. **JWT Secret Rotation:** Change `JWT_SECRET` periodically and revoke all tokens
2. **API Signature Keys:** Rotate signature keys in `ApiSignatureProperties`
3. **HTTPS Enforcement:** Auth service requires HTTPS; configure `HttpsRedirectConfig`
4. **SQL Injection Prevention:** `SqlInjectionFilter` runs before authentication; tune patterns carefully
5. **Rate Limiting:** Configure per-endpoint limits in Sentinel rules via Nacos

### Data Scope Caveats

- **Alias Requirements:** `@DataScope` requires correct table aliases matching your SQL
- **Performance:** DEPT_AND_CHILDREN uses recursive CTE; may be slow on deep hierarchies
- **Thread Safety:** Context is ThreadLocal; safe for async only if propagated manually

### Caching Pitfalls

- **Cache Eviction:** Always evict L1+L2 together via `MultiLevelCache.evict()`
- **Serialization:** Ensure cached objects are serializable (for Redis)
- **Cache Warming:** Use `@PostConstruct` to pre-load critical caches

### Message Publishing

- **Idempotency:** Always check message ID for duplicate processing
- **Ordering:** Use `sendOrderly()` with partition key for strict ordering
- **DLQ Handling:** Monitor dead-letter queues for failed messages

## Database Conventions

### Primary Keys

Use UUIDv7 for all entities:
```java
@TableId(type = IdType.ASSIGN_ID)
private Long id;  // Actually stores UUIDv7 as BINARY(16) in PostgreSQL
```

Generated via `UUIDv7Util.generate()` - time-ordered for better index performance.

### Soft Deletes

Use `deleted` column (0 = active, 1 = deleted):
```java
@TableLogic(value = "0", delval = "1")
private Integer deleted;
```

### Audit Fields

Standard audit columns:
```java
private LocalDateTime createTime;
private Long createBy;
private LocalDateTime updateTime;
private Long updateBy;
```

MyBatis-Plus auto-populates these via `MetaObjectHandler`.

## Monitoring & Troubleshooting

### Checking Service Health

```bash
# Gateway health
curl http://localhost:9095/actuator/health

# Auth service health (HTTPS required)
curl -k https://localhost:8106/actuator/health

# System service health
curl http://localhost:8081/actuator/health
```

### Viewing Metrics

```bash
# Prometheus metrics
curl http://localhost:9095/actuator/prometheus

# Sentinel dashboard
# Access at http://localhost:8858 (if running)
```

### Common Issues

**Issue: Services fail to register with Nacos**
- Verify Nacos is running: `docker ps | grep nacos`
- Check `spring.cloud.nacos.discovery.server-addr` in config
- Ensure network connectivity: `curl http://localhost:8848/nacos`

**Issue: JWT validation fails**
- Check `JWT_SECRET` environment variable matches across all services
- Verify token hasn't been blacklisted: `redis-cli GET jwt:blacklist:{jti}`
- Check token expiration: `JwtUtils.parseToken(token).getExpiration()`

**Issue: Data scope not applying**
- Verify `@DataScope` annotation is on service method, not controller
- Check `DataScopeContextHolder` has filter set (enable debug logging)
- Ensure MyBatis interceptor is registered in `MybatisPlusConfig`

**Issue: Dubbo service not found**
- Verify service provider started before consumer
- Check Nacos registry: `curl http://localhost:8848/nacos/v1/ns/instance/list?serviceName={service}`
- Review Dubbo timeout configuration (default 3000ms may be too short)

## Code Style & Conventions

- **Commit Messages:** Follow [Conventional Commits](https://www.conventionalcommits.org/) - `feat:`, `fix:`, `docs:`, etc.
- **Java Style:** Google Java Style Guide
- **Lombok:** Widely used - prefer `@Data`, `@Builder`, `@Slf4j`
- **Response Format:** Always return `ApiResponse<T>` from controllers
- **Error Handling:** Throw specific exceptions; let `GlobalExceptionHandler` format responses
- **Validation:** Use `@Valid` with JSR-303 annotations on DTOs
