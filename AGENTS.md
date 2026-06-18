# Repository Guidelines

## Project Structure & Module Organization
- Multi-module Maven root (`pom.xml`) with shared code in `common` (core, data, integration, monitoring, web/securityCore) and services in `system` (api, service), `auth`, and `gateway`. Ops assets sit in `charts`, `config`, `scripts`, and docs in `docs`.
- Service code follows the standard layout under `src/main/java` and `src/main/resources`; tests live in `src/test/java`. Example: `gateway/src/main/java/...` holds the edge API, while `system/service/src/main/java/...` contains domain services and persistence layers.

## Build, Test, and Development Commands
- `mvn clean install` from the repo root builds every module with Java 21 and resolves shared dependencies.
- `mvn clean install -DskipTests` for faster feedback when tests are known to pass.
- Run an individual service with dependencies: `mvn spring-boot:run -pl gateway -am` (swap `gateway` for `auth` or `system/service` when needed).
- Targeted testing: `mvn test -pl system/service` or `mvn -pl common/core test` to keep cycles short.
- **Test script:** `./run-tests.sh` with flags: `--security` (JWT/permissions/SQL injection tests), `--service` (service layer), `--coverage` (JaCoCo report), `--integration` (requires Docker).

## Java Version
- pom.xml specifies Java 21 (`maven.compiler.source=21`). CI workflow uses Java 17. Ensure local JDK matches pom.xml (21) for consistency.

## Coding Style & Naming Conventions
- Java 21, Spring Boot 4/Spring Cloud stack; Lombok is enabled, so prefer annotations over manual boilerplate.
- Use 4-space indentation, `PascalCase` for classes, `camelCase` for fields/methods, and `UPPER_SNAKE_CASE` for constants. Keep packages under `com.<domain>.<area>` (e.g., `com...gateway.web`).
- Configuration stays in YAML under each service’s resources; keep shared settings in the `config`/Nacos entries referenced in README and avoid hard-coded secrets.

## Testing Guidelines
- Name test classes with the `*Test` suffix and mirror the package of the code under test.
- Use unit tests for business logic in `common` and `system` modules; reserve Spring `@SpringBootTest`/web tests for gateway/auth endpoints and security flows.
- Stub external systems (Nacos, MQ, Postgres) or use testcontainers/local fakes to keep tests deterministic. Validate auth/permission flows when touching security-sensitive code.

## Commit & Pull Request Guidelines
- Follow the existing conventional style: `type(scope): summary`, with scopes matching modules (e.g., `fix(gateway,common.security): ...`). Prefer concise English or bilingual summaries when relevant.
- Before opening a PR, ensure `mvn test` (or targeted module tests) pass, describe the change and risk, link issues/task IDs, and add config notes or screenshots for observable changes.
- Keep PRs focused; include rollout steps if config or Nacos entries must be updated.

## Security & Configuration Tips
- Required env vars for local runs: `NACOS_SERVER`, `NACOS_NAMESPACE`, `NACOS_GROUP` (defaults documented in README). Bring up Nacos first, then start `system-service`, `auth-service`, and finally `gateway-service`.
- Do not commit credentials; use env vars or local overrides. When adding new services, register their Nacos config and exposure in `charts`/deployment manifests alongside application changes.

## Additional Resources
- `CLAUDE.md`: Detailed architecture patterns, security components, data scope, caching, messaging, and development patterns.
- `opencode.jsonc`: CodeGraph MCP server configuration (when `.codegraph/` directory exists).
