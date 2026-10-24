package com.crcit.openplatform.authorization;

import com.crcit.openplatform.auth.AuthAttributes;
import com.crcit.openplatform.auth.AuthContext;
import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.common.FilterError;
import com.crcit.openplatform.common.FilterOrder;
import com.crcit.openplatform.common.PublicPaths;
import com.crcit.openplatform.enterprise.ApiDefinition;
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
 * 企业级授权过滤器。
 *
 * <p>前置条件：认证过滤器已写入 {@code AuthContext}，API 映射过滤器已写入 {@code ApiDefinition}。
 * 具体规则全部委托给 {@link EnterpriseAuthorizationService}，过滤器只负责把业务异常转换成
 * HTTP 401/403/404。</p>
 */
@Component
@Order(FilterOrder.AUTHORIZATION)
public class AuthorizationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationFilter.class);

    private final EnterpriseAuthorizationService authorizationService;
    private final ObjectMapper objectMapper;

    public AuthorizationFilter(EnterpriseAuthorizationService authorizationService, ObjectMapper objectMapper) {
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicPaths.isPublic(request) || PublicPaths.isPortal(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            AuthContext context = (AuthContext) request.getAttribute(AuthAttributes.AUTH_CONTEXT);
            ApiDefinition api = (ApiDefinition) request.getAttribute(AuthAttributes.API_DEFINITION);
            if (context == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "missing auth context");
            }
            if (api == null) {
                throw new BizException(ErrorCode.API_NOT_FOUND, "missing api definition");
            }
            authorizationService.authorize(context, api);
            log.debug("企业授权通过 appId={} enterpriseId={} apiId={}",
                    context.appId(), context.enterpriseId(), api.apiId());
        } catch (BizException e) {
            int status = switch (e.getErrorCode()) {
                case UNAUTHORIZED, TOKEN_INVALID -> HttpServletResponse.SC_UNAUTHORIZED;
                case API_NOT_FOUND -> HttpServletResponse.SC_NOT_FOUND;
                default -> HttpServletResponse.SC_FORBIDDEN;
            };
            log.warn("企业授权失败 status={} code={} reason={} path={}",
                    status, e.getErrorCode().code(), e.getMessage(), request.getRequestURI());
            FilterError.write(response, objectMapper, status, e.getErrorCode(), e.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }
}
