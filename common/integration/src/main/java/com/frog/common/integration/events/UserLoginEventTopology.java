package com.frog.common.integration.events;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserLoginEventTopology {

    @Bean
    public DirectExchange userLoginExchange() {
        return new DirectExchange(UserLoginEventChannels.EXCHANGE, true, false);
    }

    @Bean
    public Queue userLoginQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(UserLoginEventChannels.QUEUE).build();
    }

    @Bean
    public Binding userLoginBinding(Queue userLoginQueue, DirectExchange userLoginExchange) {
        return BindingBuilder.bind(userLoginQueue)
                .to(userLoginExchange)
                .with(UserLoginEventChannels.ROUTING_KEY);
    }
}
