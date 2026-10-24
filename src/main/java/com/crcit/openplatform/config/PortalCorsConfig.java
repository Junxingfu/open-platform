package com.crcit.openplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 门户 API 的 CORS 配置。
 *
 * <p>开发时前端 Vite 默认跑在 5173，通过代理访问后端；如果直接用浏览器跨域访问，
 * 需要这里允许来源。生产环境应由 Nginx/网关统一配置，不要放开成 {@code *}。</p>
 */
@Configuration
public class PortalCorsConfig implements WebMvcConfigurer {

    private final OpenPlatformProperties properties;

    public PortalCorsConfig(OpenPlatformProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = properties.getPortal().getAllowedOrigins().split(",");
        for (int i = 0; i < origins.length; i++) {
            origins[i] = origins[i].trim();
        }
        registry.addMapping("/portal/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "X-Trace-Id")
                .maxAge(3600);
    }
}
