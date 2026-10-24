package com.crcit.openplatform.portal;

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
import java.util.UUID;

/**
 * 门户登录 token 的签发与校验。
 *
 * <p><b>与开放 API token 隔离</b>：本服务使用独立的 RSA 密钥、独立的 issuer/audience，
 * 因此门户 token 不能调用开放 API，开放 API token 也不能登录门户。</p>
 *
 * <p>骨架阶段启动时生成临时密钥，重启后需要重新登录；生产环境应改为 KMS/PEM + kid 轮换。</p>
 */
@Service
public class PortalTokenService {

    private static final Logger log = LoggerFactory.getLogger(PortalTokenService.class);

    private static final String ISSUER = "open-platform-portal";
    private static final String AUDIENCE = "portal-console";
    private static final String KID = "portal-local-dev";

    private final OpenPlatformProperties properties;
    private final KeyPair keyPair;
    private final JWSSigner signer;

    public PortalTokenService(OpenPlatformProperties properties) {
        this.properties = properties;
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
            this.signer = new RSASSASigner(keyPair.getPrivate());
            log.info("已生成本地开发用门户 RSA 密钥对（kid={}），门户 token TTL={}",
                    KID, properties.getPortal().getTokenTtl());
        } catch (Exception e) {
            throw new IllegalStateException("cannot init portal RSA key pair", e);
        }
    }

    public String issue(PortalUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getPortal().getTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(user.id())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .claim("name", user.name())
                .claim("email", user.email())
                .claim("ent_id", user.enterpriseId())
                .claim("role", user.role().name())
                .build();
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KID)
                    .type(JOSEObjectType.JWT)
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            log.debug("签发门户 token userId={} role={} expiresAt={}",
                    user.id(), user.role(), expiresAt);
            return jwt.serialize();
        } catch (JOSEException e) {
            log.error("门户 token 签名失败 userId={}", user.id(), e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "cannot sign portal token");
        }
    }

    public PortalPrincipal verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw invalid("unsupported alg");
            }
            JWSVerifier verifier = new RSASSAVerifier((RSAPublicKey) keyPair.getPublic());
            if (!jwt.verify(verifier)) {
                throw invalid("signature verification failed");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Instant now = Instant.now();
            if (!ISSUER.equals(claims.getIssuer())) {
                throw invalid("issuer mismatch");
            }
            if (claims.getAudience() == null || !claims.getAudience().contains(AUDIENCE)) {
                throw invalid("audience mismatch");
            }
            if (claims.getExpirationTime() == null
                    || claims.getExpirationTime().toInstant().isBefore(now)) {
                throw invalid("token expired");
            }
            String role = claims.getStringClaim("role");
            PortalRole parsedRole;
            try {
                parsedRole = PortalRole.valueOf(role);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw invalid("role missing");
            }
            return new PortalPrincipal(
                    claims.getSubject(),
                    claims.getStringClaim("name"),
                    claims.getStringClaim("email"),
                    claims.getStringClaim("ent_id"),
                    parsedRole);
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            log.debug("门户 token 校验失败: {}", e.getMessage());
            throw new BizException(ErrorCode.UNAUTHORIZED, "portal token invalid");
        }
    }

    private BizException invalid(String reason) {
        log.debug("门户 token 校验失败: {}", reason);
        return new BizException(ErrorCode.UNAUTHORIZED, "portal token invalid");
    }
}
