package com.frog.common.monitoring.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationRegistry.ObservationConfig;
import io.micrometer.observation.ObservationTextPublisher;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.semconv.ServiceAttributes;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(MonitoringProperties.class)
@RequiredArgsConstructor
public class ObservabilityAutoConfiguration {
    private final MonitoringProperties properties;

    @Bean
    @ConditionalOnMissingBean
    public SdkTracerProvider sdkTracerProvider() {
        MonitoringProperties.Otel otel = properties.getOtel();
        if (!otel.isEnabled()) {
            return SdkTracerProvider.builder().build();
        }
        Resource resource = createResource();
        SdkTracerProviderBuilder builder = SdkTracerProvider.builder()
                .setResource(resource);
        if (otel.isExporterEnabled()) {
            SpanExporter exporter = OtlpGrpcSpanExporter.builder()
                    .setEndpoint(otel.getEndpoint())
                    .setTimeout(otel.getExporterTimeout())
                    .build();
            builder.addSpanProcessor(BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(otel.getScheduleDelay())
                    .build());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(SdkTracerProvider tracerProvider) {
        TextMapPropagator compositePropagator = TextMapPropagator.composite(
                        W3CTraceContextPropagator.getInstance(),
                        B3Propagator.injectingMultiHeaders()
        );
        ContextPropagators propagators = ContextPropagators.create(compositePropagator);
        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(propagators)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        String scope = properties.getOtel().getInstrumentationScope();
        return openTelemetry.getTracer(scope);
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry(ObjectProvider<ObservationHandler<?>> handlers) {
        ObservationRegistry registry = ObservationRegistry.create();
        ObservationConfig config = registry.observationConfig();
        handlers.orderedStream().forEach(config::observationHandler);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationHandler<Observation.Context> defaultObservationHandler() {
        return new ObservationTextPublisher();
    }

    private Resource createResource() {
        AttributesBuilder builder = Attributes.builder()
                .put(ServiceAttributes.SERVICE_NAME, properties.getServiceName());
        properties.getResourceAttributes()
                .forEach((key, value) -> builder.put(AttributeKey.stringKey(key), value));
        Attributes attributes = builder.build();
        return Resource.getDefault().merge(Resource.create(attributes));
    }
}
