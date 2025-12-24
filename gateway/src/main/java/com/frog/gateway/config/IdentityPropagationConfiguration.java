package com.frog.gateway.config;

import com.frog.gateway.properties.IdentityPropagationProperties;
import com.frog.gateway.security.IdentityTokenEncoder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IdentityPropagationProperties.class)
public class IdentityPropagationConfiguration {

    @Bean
    public IdentityTokenEncoder identityTokenEncoder(IdentityPropagationProperties properties) {
        return new IdentityTokenEncoder(properties.getSignatureSecret());
    }
}
