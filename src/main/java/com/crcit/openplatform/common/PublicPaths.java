package com.crcit.openplatform.common;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * 路径分类工具。
 *
 * <p>系统有两套完全独立的入口：</p>
 * <ul>
 *   <li>{@code /open/**}：开放 API，走 OAuth2/签名认证；</li>
 *   <li>{@code /portal/**}：开放平台控制台 API，走门户登录 token。</li>
 * </ul>
 *
 * <p>两套 token 的 issuer/audience/密钥都不同，过滤器必须互相跳过对方的路径，否则门户请求
 * 会被开放 API 的认证逻辑拦成 401。公开路径白名单仍然只用于 {@code /open/**}。</p>
 *
 * <p>比较时使用的是“去掉 context-path 后的路径”，因此即使以后配置了
 * {@code server.servlet.context-path} 也不会失效。</p>
 */
public final class PublicPaths {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/open/oauth2/token",
            "/open/v1/ping",
            "/actuator/health",
            "/actuator/info"
    );

    private static final String PORTAL_PREFIX = "/portal/";

    private PublicPaths() {
    }

    public static boolean isPublic(HttpServletRequest request) {
        return PUBLIC_PATHS.contains(path(request));
    }

    /**
     * 是否属于门户控制台路径。
     *
     * <p>注意：这里返回 true 只代表“不归开放 API 过滤器管”，并不代表公开；
     * 门户路径是否放行由 {@code PortalAuthFilter} 决定。</p>
     */
    public static boolean isPortal(HttpServletRequest request) {
        return path(request).startsWith(PORTAL_PREFIX);
    }

    public static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
