package com.frog.common.integration.events;

import com.frog.common.integration.messaging.InstrumentedKafkaConsumer;
import com.frog.common.integration.model.MessageEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserLoginKafkaConsumer {
    private final InstrumentedKafkaConsumer consumer;

    @KafkaListener(topics = UserLoginKafkaChannels.TOPIC, groupId = "user-login-consumer")
    public void onMessage(@Payload MessageEnvelope<UserLoginEvent> envelope) {
        consumer.consume("user-login-kafka-consumer", envelope, event ->
                log.info("Kafka user login event userId={}, ip={}", event.getUserId(), event.getIpAddress())
        );
    }
}
