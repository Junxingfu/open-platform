package com.crcit.openplatform.authorization;

import com.crcit.openplatform.auth.AuthAttributes;
import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.common.FilterError;
import com.crcit.openplatform.common.FilterOrder;
import com.crcit.openplatform.common.PublicPaths;
import com.crcit.openplatform.enterprise.ApiDefinition;
import com.crcit.openplatform.enterprise.AppRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 映射过滤器：把「HTTP 方法 + 路径」映射到平台的 {@link ApiDefinition}。
 *
 * <p>映射成功后把 API 定义放入 request attribute，后续授权过滤器据此拿到
 * {@code productCode/requiredScope}。如果路径没有在注册表里登记，直接 404，
 * 避免“未登记接口被意外暴露”。</p>
 */
@Component
@Order(FilterOrder.API_MAPPING)
public class ApiMappingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiMappingFilter.class);

    private final AppRegistry registry;
    private final ObjectMapper objectMapper;

    public ApiMappingFilter(AppRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicPaths.isPublic(request) || PublicPaths.isPortal(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ApiDefinition api;
        try {
            String path = requestPath(request);
            api = registry.findApi(request.getMethod(), path)
                    .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "api not registered"));
            request.setAttribute(AuthAttributes.API_DEFINITION, api);
            log.debug("API 映射成功 method={} path={} apiId={} product={}",
                    request.getMethod(), path, api.apiId(), api.productCode());
        } catch (BizException e) {
            log.warn("API 映射失败 method={} path={} reason={}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            FilterError.write(response, objectMapper, HttpServletResponse.SC_NOT_FOUND,
                    e.getErrorCode(), e.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    /** 注册表里的 path 不含 context-path，这里做一次归一化。 */
    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
