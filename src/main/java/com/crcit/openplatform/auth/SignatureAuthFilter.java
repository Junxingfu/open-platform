package com.crcit.openplatform.auth;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.CachedBodyHttpServletRequest;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.common.FilterError;
import com.crcit.openplatform.common.FilterOrder;
import com.crcit.openplatform.common.PublicPaths;
import com.crcit.openplatform.config.OpenPlatformProperties;
import com.crcit.openplatform.enterprise.AppRegistration;
import com.crcit.openplatform.enterprise.AppRegistry;
import com.crcit.openplatform.replay.ReplayGuard;
import com.crcit.openplatform.signature.SignatureService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * AppKey/AppSecret + HMAC-SHA256 签名认证过滤器。
 *
 * <p>校验顺序（任何一步失败即返回 401/403）：</p>
 * <ol>
 *   <li>必填请求头是否齐全；签名算法是否为 HMAC-SHA256；</li>
 *   <li>应用是否存在、启用、且认证模式为签名；</li>
 *   <li>时间戳是否在允许偏移内（防旧请求重放）；</li>
 *   <li>body 摘要是否与 {@code Content-SHA256} 一致（防 body 被篡改）；</li>
 *   <li>HMAC 签名是否与客户端一致；</li>
 *   <li>nonce 是否首次出现（防同一请求重复提交）。</li>
 * </ol>
 *
 * <p>待签名串格式见 {@link SignatureService#canonicalString}，字段之间用换行分隔。</p>
 */
@Component
@Order(FilterOrder.SIGNATURE)
public class SignatureAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SignatureAuthFilter.class);

    private static final String HMAC_SHA256 = "HMAC-SHA256";

    private final ObjectMapper objectMapper;
    private final AppRegistry registry;
    private final ReplayGuard replayGuard;
    private final SignatureService signatureService;
    private final OpenPlatformProperties properties;

    public SignatureAuthFilter(ObjectMapper objectMapper, AppRegistry registry, ReplayGuard replayGuard,
                               SignatureService signatureService, OpenPlatformProperties properties) {
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.replayGuard = replayGuard;
        this.signatureService = signatureService;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PublicPaths.isPublic(request)
                || PublicPaths.isPortal(request)
                || request.getAttribute(AuthAttributes.AUTH_MODE) != AuthMode.SIGNATURE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String appId = requiredHeader(request, "X-App-Id");
            String timestamp = requiredHeader(request, "X-Timestamp");
            String nonce = requiredHeader(request, "X-Nonce");
            String signature = requiredHeader(request, "X-Signature");
            String method = requiredHeader(request, "X-Signature-Method");
            String contentSha = requiredHeader(request, "Content-SHA256");

            if (!HMAC_SHA256.equalsIgnoreCase(method)) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "unsupported signature method");
            }
            AppRegistration app = registry.findApp(appId)
                    .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "invalid app"));
            if (!app.active()) {
                throw new BizException(ErrorCode.FORBIDDEN, "app disabled");
            }
            if (app.authMode() != AuthMode.SIGNATURE) {
                throw new BizException(ErrorCode.FORBIDDEN, "app not enabled for signature");
            }

            // 时间戳使用毫秒。先校验时间窗，过期请求连签名都不用算。
            long requestTime;
            try {
                requestTime = Long.parseLong(timestamp);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.TIMESTAMP_INVALID, "invalid timestamp");
            }
            long skew = properties.getReplay().getTimestampSkew().toMillis();
            if (Math.abs(System.currentTimeMillis() - requestTime) > skew) {
                throw new BizException(ErrorCode.TIMESTAMP_INVALID, "timestamp expired");
            }

            // 请求体摘要：request 已被 RequestCachingFilter 包装，可安全重复读取。
            byte[] body = request instanceof CachedBodyHttpServletRequest cached
                    ? cached.getCachedBody() : new byte[0];
            String actualSha = signatureService.sha256Hex(body);
            if (!MessageDigest.isEqual(actualSha.getBytes(StandardCharsets.UTF_8),
                    contentSha.getBytes(StandardCharsets.UTF_8))) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "content digest mismatch");
            }

            // 重算待签名串并校验 HMAC。路径要排除 context-path，保证与文档中的签名规则一致。
            String canonical = signatureService.canonicalString(
                    request.getMethod(), requestPath(request), request.getQueryString(),
                    timestamp, nonce, body);
            if (!signatureService.verifyHmacSha256(app.appSecret(), canonical, signature)) {
                throw new BizException(ErrorCode.SIGNATURE_INVALID, "signature mismatch");
            }

            // 关键顺序：只有签名验证通过后才登记 nonce。
            // 否则攻击者可以用任意 nonce 刷接口，把合法客户端的 nonce 提前“烧掉”（DoS）。
            if (!replayGuard.markNonce(appId, nonce, properties.getReplay().getNonceTtl())) {
                throw new BizException(ErrorCode.NONCE_REPLAY, "nonce replayed");
            }

            request.setAttribute(AuthAttributes.AUTH_CONTEXT,
                    new AuthContext(app.enterpriseId(), appId, AuthMode.SIGNATURE,
                            registry.scopesForApp(appId), null));
            log.debug("签名认证成功 appId={} enterpriseId={} path={}",
                    appId, app.enterpriseId(), request.getRequestURI());
        } catch (BizException e) {
            int status = e.getErrorCode() == ErrorCode.FORBIDDEN
                    ? HttpServletResponse.SC_FORBIDDEN : HttpServletResponse.SC_UNAUTHORIZED;
            log.warn("签名认证失败 status={} code={} reason={} path={}",
                    status, e.getErrorCode().code(), e.getMessage(), request.getRequestURI());
            FilterError.write(response, objectMapper, status, e.getErrorCode(), e.getMessage());
            return;
        }
        chain.doFilter(request, response);
    }

    /** 取出去掉 context-path 后的请求路径，与对外文档中的签名路径保持一致。 */
    private String requestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath))
                ? uri.substring(contextPath.length())
                : uri;
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.SIGNATURE_INVALID, "missing header " + name);
        }
        return value;
    }
}
