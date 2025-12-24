package com.frog.gateway.config;

import com.frog.gateway.properties.IpAccessControlProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit configuration hook for IP control properties binding.
 */
@Configuration
@EnableConfigurationProperties(IpAccessControlProperties.class)
public class IpAccessControlConfiguration { }
