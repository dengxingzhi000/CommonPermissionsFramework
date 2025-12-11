# Monitoring Module

This module now exposes a lightweight observability starter that configures OpenTelemetry tracing, Micrometer observations, and Sentinel rule management for every service that depends on com.frog.common:monitoring.

## Key capabilities

- **OpenTelemetry Tracing** 每 ObservabilityAutoConfiguration boots an OpenTelemetrySdk with OTLP exporter and W3C/B3 propagators. A Tracer bean is available everywhere, and the @BusinessTrace aspect now emits spans automatically.
- **Micrometer Observation Registry** 每 a shared ObservationRegistry is registered so components can publish timers/counters through Micrometer＊s Observation API.
- **Dynamic Sentinel Rules** 每 SentinelAutoConfiguration loads flow/degrade rules from configuration and reloads when Spring Cloud EnvironmentChangeEvent fires while wiring a customizable BlockExceptionHandler.

## Configuration

`
monitoring:
  service-name: order-service
  resource-attributes:
    deployment.environment: prod
  otel:
    enabled: true
    endpoint: http://otel-collector:4317
    exporter-enabled: true
    schedule-delay: 5s
    exporter-timeout: 5s
  sentinel:
    enabled: true
    flow-rules:
      - resource: /api/order/create
        grade: 1        # QPS
        count: 200
        control-behavior: 0
    degrade-rules:
      - resource: /api/order/create
        grade: 0        # RT
        count: 1000
        time-window: 15
`

Any change to monitoring.sentinel.* published through Spring Cloud Config / Nacos triggers a runtime refresh.

## Integration points

- Add the dependency to a service module and the auto-configuration registers itself via META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports.
- Use @BusinessTrace or TraceUtils.wrapRunnable 每 both now rely on OpenTelemetry (no SkyWalking toolkit dependency).
- Annotate controllers/services with @RateLimit to guard endpoints. Sentinel rules stay centralized; unused annotations (@CircuitBreaker, @ParamFlow) were removed to keep the API minimal.
- DistributedLock/other infrastructure can inject ObservationRegistry and Tracer directly if deeper instrumentation is needed.

## OTLP Export

The default exporter targets http://localhost:4317. Override monitoring.otel.endpoint or disable exporting entirely with monitoring.otel.exporter-enabled=false (traces remain in-process for logging/ID correlation).
