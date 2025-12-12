package com.frog.common.mybatisPlus.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "frog.mybatis")
@Data
public class MybatisPlusProperties {
    /**
     * 最大单页限制数量，默认 1000。
     */
    private Long paginationMaxLimit = 1000L;
}

