package com.crcit.openplatform.oauth;

import com.crcit.openplatform.auth.AuthMode;
import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.config.OpenPlatformProperties;
import com.crcit.openplatform.enterprise.AppRegistration;
import com.crcit.openplatform.enterprise.AppRegistry;
import com.crcit.openplatform.enterprise.Enterprise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OAuth2 客户端凭证模式（client_credentials）的令牌签发逻辑。
 *
 * <p>处理顺序：</p>
 * <ol>
 *   <li>按 client_id 找到应用，确认应用存在、启用、且认证模式为 OAUTH2；</li>
 *   <li>常量时间比较 client_secret（骨架阶段明文存储，生产应存哈希）；</li>
 *   <li>确认应用所属企业存在且启用；</li>
 *   <li>校验申请的 scope 是否都在企业已订阅的范围内；</li>
 *   <li>调用 {@link JwtService} 签发 15 分钟有效的 JWT。</li>
 * </ol>
 */
@Service
public class OAuthTokenService {

    private static final Logger log = LoggerFactory.getLogger(OAuthTokenService.class);

    private final AppRegistry registry;
    private final JwtService jwtService;
    private final OpenPlatformProperties properties;

    public OAuthTokenService(AppRegistry registry, JwtService jwtService, OpenPlatformProperties properties) {
        this.registry = registry;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    /**
     * 校验客户端并签发访问令牌。
     *
     * @param clientId      应用 ID（app-oauth）
     * @param clientSecret  应用密钥
     * @param requestedScope 申请的权限点，为空时默认授予该应用全部已订阅权限
     */
    public TokenResponse issue(String clientId, String clientSecret, String requestedScope) {
        AppRegistration app = registry.findApp(clientId)
                .orElseThrow(() -> {
                    log.warn("OAuth2 签发失败：应用不存在 clientId={}", clientId);
                    return new BizException(ErrorCode.UNAUTHORIZED, "invalid client");
                });
        if (!app.active() || app.authMode() != AuthMode.OAUTH2) {
            log.warn("OAuth2 签发失败：应用未启用或不支持 OAuth2 clientId={}", clientId);
            throw new BizException(ErrorCode.UNAUTHORIZED, "client not enabled for oauth2");
        }

        // 注意：MessageDigest.isEqual 是常量时间比较，避免通过响应时间猜测 secret。
        // 骨架阶段密钥明文存储；生产环境应改为加盐哈希并支持轮换。
        if (!MessageDigest.isEqual(app.appSecret().getBytes(StandardCharsets.UTF_8),
                clientSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("OAuth2 签发失败：client_secret 不匹配 clientId={}", clientId);
            throw new BizException(ErrorCode.UNAUTHORIZED, "invalid client secret");
        }

        Enterprise enterprise = registry.findEnterprise(app.enterpriseId())
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "enterprise not found"));
        if (!enterprise.active()) {
            log.warn("OAuth2 签发失败：企业已停用 clientId={} enterpriseId={}", clientId, enterprise.id());
            throw new BizException(ErrorCode.FORBIDDEN, "enterprise disabled");
        }

        Set<String> allowed = registry.scopesForApp(clientId);
        Set<String> requested = parseScopes(requestedScope);
        if (requested.isEmpty()) {
            requested = allowed;
        }
        if (!allowed.containsAll(requested)) {
            log.warn("OAuth2 签发失败：申请了未订阅的 scope clientId={} requested={} allowed={}",
                    clientId, requested, allowed);
            throw new BizException(ErrorCode.FORBIDDEN, "scope not subscribed");
        }

        String token = jwtService.issueAccessToken(clientId, enterprise.id(), requested);
        long expiresIn = properties.getAuth().getTokenTtl().toSeconds();
        log.info("OAuth2 令牌签发成功 clientId={} enterpriseId={} scopes={} expiresIn={}s",
                clientId, enterprise.id(), requested, expiresIn);
        return new TokenResponse(token, "Bearer", expiresIn, String.join(" ", requested));
    }

    /** 把 "a b" 形式的 scope 字符串解析成稳定有序的集合。 */
    private Set<String> parseScopes(String scope) {
        if (scope == null || scope.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(scope.trim().split("\\s+"))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
