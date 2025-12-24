# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CommonPermissionsFramework (NewNearSync) is an enterprise-grade microservices permission management framework built on Spring Cloud 2025, Spring Security 6, and Spring Boot 4. It provides multi-layered security with JWT authentication, RBAC authorization, data-level permissions, API signature validation, read-write separation, and event-driven architecture.

**Tech Stack:** Java 21, Spring Boot 4.0.0, Spring Cloud 2025.1.0, Spring Cloud Alibaba 2025.0.0.0, MyBatis-Plus 3.5.14, Nacos, Dubbo, Redis, PostgreSQL/MySQL, Kafka/RabbitMQ

**Project Name:** Repository is named `NewNearSync` but the framework is called `CommonPermissionsFramework`

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
# Use Docker Compose for dependencies (located in auth/src/main/resources/)
# Starts: Nacos, Redis, PostgreSQL/MySQL, Kafka/RabbitMQ
docker-compose -f auth/src/main/resources/docker-compose.yml up -d

# Verify Nacos is running
curl http://localhost:8848/nacos

# Check registered services
curl http://localhost:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=10
```

### Prerequisites

- JDK 21 (required - project uses Java 21 language features)
- Maven 3.8+
- Docker and Docker Compose (for dependencies: Nacos, Redis, PostgreSQL, Kafka/RabbitMQ)
- Nacos 2.0+

## Architecture & Code Organization

### Module Structure

```
NewNearSync/
├── common/                          # Shared foundation modules
│   ├── core/                        # Core utilities, exceptions, PageResult, UUIDv7
│   ├── security-api/                # Security DTOs and interfaces shared across services
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
- **JwtUtils** (common/web/src/.../security/util): Token generation/validation/blacklisting via Redis
- **SecurityConfig** (common/web/src/.../security/config): Filter chain configuration (order: SqlInjection → JWT → StepUp)
- **ApiSignatureFilter** (gateway/src/.../filter): HMAC-SHA256 with nonce-based replay prevention
- **ApiSignatureProperties** (gateway/src/.../properties): Moved from config package in recent refactoring
- **IdentityPropagationProperties** (gateway/src/.../properties): Gateway identity propagation settings
- **IpAccessControlProperties** (gateway/src/.../properties): IP whitelist/blacklist configuration
- **StepUpFilter** (common/web/src/.../stepup): Step-up authentication for sensitive operations

#### 2. Data Scope (Row-Level Security)

**Pattern:** Annotation-driven SQL rewriting for multi-tenant data isolation

```java
@DataScope(userAlias = "u", deptAlias = "d")
public List<SysUser> selectUserList(UserQuery query) {
    // MyBatis mapper call - SQL will be automatically rewritten
}
```

**How it works:**
1. `@DataScope` annotation triggers `DataScopeAspect` (common/data/src/.../mybatisPlus/aspect)
2. Aspect populates `DataScopeContextHolder` (ThreadLocal)
3. `DataScopeInterceptor` (MyBatis interceptor) rewrites SQL
4. Adds WHERE clause based on user's data scope level

**Data Scope Levels:** ALL (1), CUSTOM (2), DEPT (3), DEPT_AND_CHILDREN (4), SELF (5)

**File Locations:**
- Aspect: `common/data/src/main/java/com/frog/common/mybatisPlus/aspect/DataScopeAspect.java`
- Interceptor: `common/data/src/main/java/com/frog/common/mybatisPlus/interceptor/DataScopeInterceptor.java`

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

#### 4. Two-Level Caching

**Cache Hierarchy:** L1 (Caffeine) → L2 (Redis) → Database

- `TwoLevelCache`: Implements Spring Cache interface with coordinated L1+L2 and Redis pub/sub invalidation
- `TwoLevelCacheManager`: Spring CacheManager implementation supporting multiple named caches
- Supports `@Cacheable`, `@CacheEvict`, and `@CachePut` annotations
- Automatic L1 invalidation across instances via Redis pub/sub channel `cache:invalidation:twolevel`

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

**Kafka Features:**
- Idempotence enabled by default
- DLQ (Dead Letter Queue) support with configurable suffix (default: `.dlq`)
- Exponential backoff retry (configurable: initial 200ms, multiplier 2, max 5s)
- CloudEvents message envelope with distributed tracing
- Observation/OTel integration for metrics

**RabbitMQ Features:**
- sendSync/sendAsync/sendDelayed/sendOrderly modes
- Publisher Confirm and Return callbacks
- OpenTelemetry tracing
- CloudEvents message envelope
- Idempotent message consumption

**Message Envelope (CloudEvents Compliant):**
All messages use `MessageEnvelope` with: id, type, source, specVersion, time, traceId, tenant, version, and extension fields.

**Key Files:**
- Messaging interfaces: `common/integration/src/main/java/com/frog/common/integration/messaging/`
- Event examples: `common/integration/src/main/java/com/frog/common/integration/events/`
- Configuration: `common/integration/src/main/java/com/frog/common/integration/config/`

See `common/integration/Developer.md` for detailed messaging patterns and configuration templates.

#### 7. Resilience & Fault Tolerance (Resilience4j)

**Circuit Breaker Pattern:** Prevents cascading failures by monitoring service health

**Default Configuration (Auth Service):**
- Failure rate threshold: 50% (auth service: 60%)
- Slow call threshold: 2s
- Slow call rate threshold: 50%
- Sliding window: 10 calls
- Wait duration: 60s before retry
- Half-open permitted calls: 3

**Additional Resilience Features:**
- **Retry:** 3 attempts with exponential backoff (multiplier: 2)
- **Rate Limiter:** 100 requests/second (userService: 50/second)
- **Bulkhead:** Max 10 concurrent calls with 1s wait timeout

**Usage Example:**

```java
@Service
public class UserServiceClient {

    @CircuitBreaker(name = "userService", fallbackMethod = "fallbackGetUser")
    @Retry(name = "userService")
    @RateLimiter(name = "userService")
    public SysUser getUser(Long userId) {
        return feignClient.getUserById(userId);
    }

    private SysUser fallbackGetUser(Long userId, Throwable ex) {
        log.warn("Fallback triggered for user {}: {}", userId, ex.getMessage());
        return getCachedUser(userId);  // Return cached data
    }
}
```

**Monitoring Circuit Breaker:**

```bash
# View circuit breaker status
curl https://localhost:8106/actuator/circuitbreakers

# View circuit breaker events
curl https://localhost:8106/actuator/circuitbreakerevents

# View retry metrics
curl https://localhost:8106/actuator/retries

# View rate limiter metrics
curl https://localhost:8106/actuator/ratelimiters
```

**Configuration Location:** `auth/src/main/resources/application.yaml` under `resilience4j.*`

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
- **WebAuthn Support:** Auth service includes FIDO2/WebAuthn for passwordless authentication
  - Challenge expiry: 120s (auth), 300s (registration)
  - Credential inactive threshold: 90 days
  - Auth attempt retention: 30 days
  - Configuration in `auth/src/main/resources/application.yaml` under `webauthn.rp.*`

### Data Scope Caveats
- `@DataScope` requires correct table aliases matching your SQL
- DEPT_AND_CHILDREN uses recursive CTE; may be slow on deep hierarchies
- Context is ThreadLocal; propagate manually for async operations

### Caching Pitfalls
- Use Spring Cache annotations (`@CacheEvict`, `@CachePut`) or `Cache.evict()` to invalidate entries
- TwoLevelCache automatically coordinates L1+L2 eviction and notifies other instances via Redis pub/sub
- Ensure cached objects are serializable for Redis (use Jackson-compatible types)

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

# Circuit breaker monitoring (Auth service)
curl -k https://localhost:8106/actuator/circuitbreakers
curl -k https://localhost:8106/actuator/circuitbreakerevents
curl -k https://localhost:8106/actuator/retries
curl -k https://localhost:8106/actuator/ratelimiters
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
- **Package Naming:** All packages use lowercase: `com.frog.{module}.{layer}`
- **JavaDoc:** Required for all public classes and methods
- **Test Coverage:** Minimum 80% for new code

## Key File Locations

**Gateway Properties:**
- `gateway/src/main/java/com/frog/gateway/properties/` - API signature, identity propagation, IP access control

**Configuration Templates:**
- `config/templates/integration-messaging.yaml` - Kafka/RabbitMQ configuration templates

**DTOs:**
- `common/data/src/main/java/com/frog/common/dto/` - Shared DTOs across services
- `common/security-api/` - Security-related DTOs and interfaces