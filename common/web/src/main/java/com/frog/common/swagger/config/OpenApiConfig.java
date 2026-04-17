package com.frog.common.swagger.config;

import com.frog.common.swagger.properties.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 通用配置
 * <p>
 * 提供标准的 OpenAPI 文档配置，支持 JWT Bearer 认证。
 * 各服务可以通过 application.yaml 中的 springdoc.api-docs 配置来自定义文档信息。
 * </p>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-01-07
 */
@Configuration
@EnableConfigurationProperties(OpenApiProperties.class)
@ConditionalOnProperty(prefix = "springdoc.api-docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfig {
    private final OpenApiProperties properties;

    public OpenApiConfig(OpenApiProperties properties) {
        this.properties = properties;
    }

    @Bean
    public OpenAPI customOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .description(properties.getDescription())
                        .version(properties.getVersion())
                        .contact(new Contact()
                                .name(properties.getContact().getName())
                                .email(properties.getContact().getEmail())
                                .url(properties.getContact().getUrl()))
                        .license(new License()
                                .name(properties.getLicense().getName())
                                .url(properties.getLicense().getUrl())))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT 认证令牌")))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearer-jwt"));

        // 添加服务器列表（如果配置了）
        if (properties.getServers() != null && !properties.getServers().isEmpty()) {
            properties.getServers().forEach(server ->
                    openAPI.addServersItem(new io.swagger.v3.oas.models.servers.Server()
                            .url(server.getUrl())
                            .description(server.getDescription()))
            );
        }

        return openAPI;
    }
}