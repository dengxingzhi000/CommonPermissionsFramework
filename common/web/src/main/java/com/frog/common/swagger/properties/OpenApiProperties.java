package com.frog.common.swagger.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * SpringDoc OpenAPI 配置属性
 * <p>
 * 通过 application.yaml 中的 springdoc.info 配置来自定义 OpenAPI 文档信息
 * </p>
 *
 * @author Claude
 * @version 1.0
 * @since 2025-01-07
 */
@Data
@ConfigurationProperties(prefix = "springdoc.info")
public class OpenApiProperties {

    /**
     * API 标题
     */
    private String title = "API Documentation";

    /**
     * API 描述
     */
    private String description = "API Documentation";

    /**
     * API 版本
     */
    private String version = "v1.0.0";

    /**
     * 联系信息
     */
    private Contact contact = new Contact();

    /**
     * 许可证信息
     */
    private License license = new License();

    /**
     * 服务器列表
     */
    private List<Server> servers = new ArrayList<>();

    @Data
    public static class Contact {
        /**
         * 联系人姓名
         */
        private String name = "Development Team";

        /**
         * 联系人邮箱
         */
        private String email = "dev@example.com";

        /**
         * 联系人 URL
         */
        private String url = "";
    }

    @Data
    public static class License {
        /**
         * 许可证名称
         */
        private String name = "Apache 2.0";

        /**
         * 许可证 URL
         */
        private String url = "https://www.apache.org/licenses/LICENSE-2.0.html";
    }

    @Data
    public static class Server {
        /**
         * 服务器 URL
         */
        private String url;

        /**
         * 服务器描述
         */
        private String description;
    }
}