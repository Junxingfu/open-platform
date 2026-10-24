package com.crcit.openplatform.oauth;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.config.OpenPlatformProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * RS256 JWT 签发与校验。
 *
 * <p>为什么用 RS256：签名用私钥、校验用公钥。开放平台自己保存私钥，网关/资源服务只需要公钥，
 * 即使公钥泄露也无法伪造 token。</p>
 *
 * <p><b>骨架阶段限制</b>：RSA 密钥对在每次启动时随机生成，因此重启后旧 token 全部失效。
 * 生产环境应改为从 KMS 或 PEM 文件加载密钥，并通过 {@code kid} 支持平滑轮换。</p>
 *
 * <p>本类只负责“签发/验签/校验标准声明”，不负责应用状态、订阅关系等业务校验，那些在过滤器
 * 和 {@code EnterpriseAuthorizationService} 中完成。</p>
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** 本地开发密钥标识。生产环境应使用真实 kid，便于按 kid 选择公钥。 */
    private static final String KID = "local-dev";

    private final OpenPlatformProperties properties;
    private final KeyPair keyPair;
    private final JWSSigner signer;

    public JwtService(OpenPlatformProperties properties) {
        this.properties = properties;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
            this.signer = new RSASSASigner(keyPair.getPrivate());
            log.info("已生成本地开发用 RSA 密钥对（2048 位，kid={}）。服务重启后历史 token 会失效。", KID);
        } catch (Exception e) {
            throw new IllegalStateException("cannot init RSA key pair", e);
        }
    }

    /**
     * 签发外部访问令牌（OAuth2 client_credentials 流程使用）。
     *
     * @param appId        应用 ID，写入 sub 和 app_id
     * @param enterpriseId 企业 ID，写入 ent_id，后续授权过滤器据此判断订阅关系
     * @param scopes       已授权的权限点，写入 scope（空格分隔）
     */
    public String issueAccessToken(String appId, String enterpriseId, Set<String> scopes) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAuth().getTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getAuth().getIssuer())
                .subject(appId)
                .audience(List.of(properties.getAuth().getAudience()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("app_id", appId)
                .claim("ent_id", enterpriseId)
                .claim("scope", String.join(" ", scopes))
                .claim("auth_mode", "oauth2")
                .build();
        String token = sign(claims);
        // 只记录令牌元数据，绝不打印 token 或密钥本身。
        log.debug("签发 OAuth2 访问令牌 appId={} enterpriseId={} scopes={} expiresAt={}",
                appId, enterpriseId, scopes, expiresAt);
        return token;
    }

    /**
     * 签发内部调用令牌，供开放平台调用内网网关时使用。
     *
     * <p>令牌里额外带 {@code api_id/app_id/ent_id}，网关可以据此做路由白名单和审计。</p>
     */
    public String issueInternalToken(String apiId, String appId, String enterpriseId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getAuth().getInternalTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(properties.getAuth().getInternalIssuer())
                .subject("open-platform-service")
                .audience(List.of(properties.getAuth().getInternalAudience()))
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", "api:invoke")
                .claim("api_id", apiId)
                .claim("app_id", appId)
                .claim("ent_id", enterpriseId)
                .build();
        String token = sign(claims);
        log.debug("签发内部调用令牌 apiId={} appId={} enterpriseId={} expiresAt={}",
                apiId, appId, enterpriseId, expiresAt);
        return token;
    }

    /**
     * 校验 JWT：签名算法 → 签名 → 签发方 → 受众 → 过期时间 → 签发时间。
     *
     * <p>任何一步失败都抛 {@link BizException}（错误码 {@link ErrorCode#TOKEN_INVALID}），
     * 由调用方转成 HTTP 401。</p>
     */
    public JWTClaimsSet verify(String token, String expectedIssuer, String expectedAudience) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);

            // 1. 只接受 RS256，避免 alg=none、HS256 混淆等算法降级攻击。
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw invalid("unsupported alg");
            }

            // 2. 用公钥验签；verify 内部是常量时间比较。
            JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
            if (!jwt.verify(verifier)) {
                throw invalid("signature verification failed");
            }

            // 3. 校验 iss/aud/exp/iat 等标准声明。
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            validate(claims, expectedIssuer, expectedAudience);
            log.debug("JWT 校验通过 subject={} jti={}", claims.getSubject(), claims.getJWTID());
            return claims;
        } catch (ParseException | JOSEException e) {
            log.debug("JWT 解析或验签失败: {}", e.getMessage());
            throw new BizException(ErrorCode.TOKEN_INVALID, "invalid token");
        }
    }

    private void validate(JWTClaimsSet claims, String expectedIssuer, String expectedAudience) {
        Instant now = Instant.now();
        long skew = properties.getAuth().getClockSkew().toSeconds();

        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw invalid("issuer mismatch");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience)) {
            throw invalid("audience mismatch");
        }
        if (claims.getExpirationTime() == null
                || claims.getExpirationTime().toInstant().plusSeconds(skew).isBefore(now)) {
            throw invalid("token expired");
        }
        // 允许最多 clockSkew 的“未来签发时间”，防止不同机器时钟略微超前导致误判。
        if (claims.getIssueTime() != null
                && claims.getIssueTime().toInstant().minusSeconds(skew).isAfter(now)) {
            throw invalid("token issued in the future");
        }
    }

    private String sign(JWTClaimsSet claims) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KID)
                    .type(JOSEObjectType.JWT)
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            log.error("JWT 签名失败", e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "cannot sign token");
        }
    }

    private BizException invalid(String reason) {
        log.debug("JWT 校验失败: {}", reason);
        return new BizException(ErrorCode.TOKEN_INVALID, reason);
    }
}
