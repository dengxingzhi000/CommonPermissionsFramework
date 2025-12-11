# Integration Module (RabbitMQ)

Production-ready messaging starter aligned with the monitoring module (OTel + Micrometer). Supports RabbitMQ and Kafka.

## Capabilities
- CloudEvents-like `MessageEnvelope` with trace/tenant/version fields.
- RabbitMQ: reliable publisher with confirm/return handling, delayed/orderly sending helpers; consumer wrapper with tracing/metrics/idempotency.
- Kafka: publisher + listener instrumentation; observation enabled container factory.
- Auto-configures messaging beans via Spring Boot auto-configuration, JSON serialization with Jackson.

## Properties (integration.messaging.*)
- `publisher-confirms` (default: true)
- `delayed-exchange-enabled` (default: true; requires x-delayed-message plugin)
- `confirm-timeout` (default: 5s)
- `ordering-partitions` (default: 4)

## Properties (integration.kafka.*)
- `bootstrap-servers` (default: localhost:9092)
- `client-id` (default: integration-producer)
- `acks` (default: all)
- `retries`, `linger-ms`, `batch-size`, `max-in-flight`, `idempotence`
- `dlq-enabled`, `dlq-suffix` (default: ".dlq")
- `max-attempts` (includes first try), `backoff-initial`, `backoff-max`, `backoff-multiplier`

## Sample: User Login Event
- RabbitMQ: `UserLoginEventTopology` creates exchange/queue/binding; `UserLoginEventProducer.publish(...)` wraps payload into `MessageEnvelope` and `sendSync`; `UserLoginEventConsumer` uses `InstrumentedMessageConsumer` for tracing + idempotency.
- Kafka: topic defined in `UserLoginKafkaChannels`; publish with `UserLoginKafkaProducer`; consume with `UserLoginKafkaConsumer` (observation-enabled).

## Config template
See `config/templates/integration-messaging.yaml` for a starter YAML covering RabbitMQ and Kafka properties.
