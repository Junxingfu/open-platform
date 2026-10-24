package com.crcit.openplatform.auth;

import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.common.FilterError;
import com.crcit.openplatform.common.FilterOrder;
import com.crcit.openplatform.common.PublicPaths;
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
 * 认证方式解析过滤器。
 *
 * <p>它只做“二选一”的路由判断，不做真正的令牌校验：</p>
 * <ul>
 *   <li>有 {@code Authorization: Bearer ...} → 标记为 OAuth2；</li>
 *   <li>有 {@code X-App-Id} → 标记为签名模式；</li>
 *   <li>两者都没有 → 直接返回 401，不再往下走。</li>
 * </ul>
 *
 * <p>后续的 OAuthTokenFilter 和 SignatureAuthFilter 通过 {@code shouldNotFilter} 判断自己是否
 * 需要处理该请求，从而避免两套校验逻辑互相干扰。</p>
 */
@Component
@Order(FilterOrder.AUTH_MODE)
public class AuthModeResolverFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthModeResolverFilter.class);

    private final ObjectMapper objectMapper;

    public AuthModeResolverFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicPaths.isPublic(request) || PublicPaths.isPortal(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String appId = request.getHeader("X-App-Id");
        AuthMode mode;
        if (authorization != null && authorization.startsWith("Bearer ")) {
            mode = AuthMode.OAUTH2;
        } else if (appId != null && !appId.isBlank()) {
            mode = AuthMode.SIGNATURE;
        } else {
            log.debug("请求未携带任何认证凭证 method={} path={}", request.getMethod(), request.getRequestURI());
            FilterError.write(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.UNAUTHORIZED, "missing credentials");
            return;
        }
        request.setAttribute(AuthAttributes.AUTH_MODE, mode);
        log.debug("认证方式解析完成 mode={} path={}", mode, request.getRequestURI());
        chain.doFilter(request, response);
    }
}
