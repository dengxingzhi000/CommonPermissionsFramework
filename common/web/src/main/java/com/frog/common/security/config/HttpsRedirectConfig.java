package com.frog.common.security.config;

import lombok.RequiredArgsConstructor;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 *
 * @author Deng
 * createData 2025/10/24 16:13
 * @version 1.0
 */
@Configuration
@RequiredArgsConstructor
public class HttpsRedirectConfig {

    private final HttpsRedirectProperties properties;

    @Bean
    public ServletWebServerFactory servletContainer() {
        if (!properties.isEnabled()) {
            return new TomcatServletWebServerFactory();
        }
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };

        // 添加HTTP连接器（自动重定向到HTTPS）
        tomcat.addAdditionalConnectors(createHttpConnector());

        return tomcat;
    }

    private Connector createHttpConnector() {
        Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        connector.setScheme("http");
        connector.setPort(properties.getHttpPort());
        connector.setSecure(false);
        connector.setRedirectPort(properties.getRedirectPort()); // 重定向到 HTTPS端口

        return connector;
    }
}
