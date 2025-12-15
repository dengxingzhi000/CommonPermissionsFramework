package com.frog.common.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.frog.common.security.serializer.SensitiveJsonSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        // 注册脱敏序列化器
        SimpleModule module = new SimpleModule();
        module.addSerializer(String.class, new SensitiveJsonSerializer());
        objectMapper.registerModule(module);
        
        // 注册JavaTimeModule以支持Java 8时间类型序列化
        objectMapper.registerModule(new JavaTimeModule());
        
        return objectMapper;
    }
}