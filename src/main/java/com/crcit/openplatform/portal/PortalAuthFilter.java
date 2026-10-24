package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.common.FilterError;
import com.crcit.openplatform.common.FilterOrder;
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
 * 门户 API 的登录校验过滤器。
 *
 * <p>只拦截 {@code /portal/api/**}，其中登录接口和 OPTIONS 预检放行。其余请求必须携带
 * {@code Authorization: Bearer <portal-token>}，校验通过后把 {@link PortalPrincipal}
 * 写入 request attribute，控制器通过 {@link PortalControllerSupport} 获取。</p>
 *
 * <p>与开放 API 的认证过滤器完全独立：门户 token 不能调用 {@code /open/**}，
 * 开放 API token 也不能访问 {@code /portal/api/**}。</p>
 */
@Component
@Order(FilterOrder.PORTAL_AUTH)
public class PortalAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthFilter.class);

    private final ObjectMapper objectMapper;
    private final PortalTokenService tokenService;

    public PortalAuthFilter(ObjectMapper objectMapper, PortalTokenService tokenService) {
        this.objectMapper = objectMapper;
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return !path.startsWith("/portal/api/")
                || "/portal/api/auth/login".equals(path)
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            deny(response, "缺少门户登录凭证");
            return;
        }
        try {
            PortalPrincipal principal = tokenService.verify(authorization.substring("Bearer ".length()).trim());
            request.setAttribute(PortalAttributes.PRINCIPAL, principal);
            log.debug("门户认证通过 userId={} role={} path={}",
                    principal.userId(), principal.role(), request.getRequestURI());
            chain.doFilter(request, response);
        } catch (BizException e) {
            log.debug("门户认证失败 path={} reason={}", request.getRequestURI(), e.getMessage());
            deny(response, "登录状态已失效，请重新登录");
        } catch (Exception e) {
            log.warn("门户认证异常 path={} error={}", request.getRequestURI(), e.getMessage());
            deny(response, "登录状态已失效，请重新登录");
        }
    }

    private void deny(HttpServletResponse response, String message) throws IOException {
        FilterError.write(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED, message);
    }

    private String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }
}
