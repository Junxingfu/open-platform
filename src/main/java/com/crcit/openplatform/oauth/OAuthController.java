package com.crcit.openplatform.oauth;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2 令牌端点。
 *
 * <p>使用标准的 {@code application/x-www-form-urlencoded} 请求：</p>
 * <pre>
 * POST /open/oauth2/token
 * grant_type=client_credentials&amp;client_id=app-oauth&amp;client_secret=oauth-secret&amp;scope=enterprise.read
 * </pre>
 *
 * <p>该路径在 {@code PublicPaths} 中是公开的，不经过 OAuth/签名过滤器；令牌校验由业务方法完成。</p>
 */
@RestController
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthTokenService tokenService;

    public OAuthController(OAuthTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @PostMapping(value = "/open/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public TokenResponse token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret,
            @RequestParam(value = "scope", required = false) String scope) {
        log.debug("收到 OAuth2 令牌请求 grantType={} clientId={} scope={}", grantType, clientId, scope);
        if (!"client_credentials".equals(grantType)) {
            log.warn("不支持的 grant_type={}", grantType);
            throw new BizException(ErrorCode.UNAUTHORIZED, "unsupported grant_type");
        }
        return tokenService.issue(clientId, clientSecret, scope);
    }
}
