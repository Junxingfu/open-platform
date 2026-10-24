package com.crcit.openplatform.auth;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.common.FilterError;
import com.crcit.openplatform.common.FilterOrder;
import com.crcit.openplatform.common.PublicPaths;
import com.crcit.openplatform.config.OpenPlatformProperties;
import com.crcit.openplatform.enterprise.AppRegistration;
import com.crcit.openplatform.enterprise.AppRegistry;
import com.crcit.openplatform.oauth.JwtService;
import com.crcit.openplatform.replay.ReplayGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OAuth2 Bearer Token 认证过滤器。
 *
 * <p>校验链路：JWT 签名/iss/aud/exp → 应用是否存在且启用 → jti 是否被撤销 →
 * （可选）jti 严格一次性 → 解析 scope 写入 {@link AuthContext}。</p>
 *
 * <p>注意：这里不校验企业订阅和具体 API 权限，那是后续 AuthorizationFilter 的职责，
 * 这样认证与授权职责分离，便于单独测试。</p>
 */
@Component
@Order(FilterOrder.OAUTH)
public class OAuthTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenFilter.class);

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final AppRegistry registry;
    private final ReplayGuard replayGuard;
    private final OpenPlatformProperties properties;

    public OAuthTokenFilter(ObjectMapper objectMapper, JwtService jwtService, AppRegistry registry,
                            ReplayGuard replayGuard, OpenPlatformProperties properties) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.registry = registry;
        this.replayGuard = replayGuard;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicPaths.isPublic(request)
                || PublicPaths.isPortal(request)
                || request.getAttribute(AuthAttributes.AUTH_MODE) != AuthMode.OAUTH2;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String authorization = request.getHeader("Authorization");
            String token = authorization.substring("Bearer ".length()).trim();

            // 1. 验签 + 校验标准声明；失败会抛 TOKEN_INVALID。
            JWTClaimsSet claims = jwtService.verify(token,
                    properties.getAuth().getIssuer(), properties.getAuth().getAudience());

            // 2. 从自定义 claim 中取出业务身份。
            String appId = claims.getStringClaim("app_id");
            String enterpriseId = claims.getStringClaim("ent_id");
            String jti = claims.getJWTID();
            String scope = claims.getStringClaim("scope");
            if (appId == null || enterpriseId == null) {
                throw new BizException(ErrorCode.TOKEN_INVALID, "token missing app_id/ent_id");
            }

            // 3. 应用必须仍然存在且启用（token 有效期内可能被管理员停用）。
            AppRegistration app = registry.findApp(appId)
                    .orElseThrow(() -> new BizException(ErrorCode.TOKEN_INVALID, "app not found"));
            if (!app.active()) {
                throw new BizException(ErrorCode.FORBIDDEN, "app disabled");
            }

            // 4. jti 撤销/一次性检查。默认只查撤销表，不强制一次性。
            if (jti != null && replayGuard.isJtiRevoked(jti)) {
                throw new BizException(ErrorCode.TOKEN_INVALID, "token revoked");
            }
            if (jti != null && properties.getAuth().isJtiOneTime()
                    && !replayGuard.markJtiOnce(jti, properties.getAuth().getTokenTtl())) {
                throw new BizException(ErrorCode.TOKEN_INVALID, "token already used");
            }

            // 5. 写入认证上下文，后续授权过滤器直接使用。
            Set<String> scopes = parseScopes(scope);
            request.setAttribute(AuthAttributes.AUTH_CONTEXT,
                    new AuthContext(enterpriseId, appId, AuthMode.OAUTH2, scopes, jti));
            log.debug("OAuth2 认证成功 appId={} enterpriseId={} scopes={} path={}",
                    appId, enterpriseId, scopes, request.getRequestURI());
        } catch (BizException e) {
            int status = e.getErrorCode() == ErrorCode.FORBIDDEN
                    ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED;
            log.warn("OAuth2 认证失败 status={} code={} reason={} path={}",
                    status, e.getErrorCode().code(), e.getMessage(), request.getRequestURI());
            FilterError.write(response, objectMapper, status, e.getErrorCode(), e.getMessage());
            return;
        } catch (Exception e) {
            // 兜底：任何解析异常都不应把堆栈暴露给调用方。
            log.warn("OAuth2 认证异常 path={} error={}", request.getRequestURI(), e.getMessage());
            FilterError.write(response, objectMapper, HttpServletResponse.SC_UNAUTHORIZED,
                    ErrorCode.TOKEN_INVALID, "token invalid");
            return;
        }
        chain.doFilter(request, response);
    }

    private Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scope.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
