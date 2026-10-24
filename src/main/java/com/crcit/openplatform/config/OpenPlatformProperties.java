package com.crcit.openplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 开放平台业务配置。
 *
 * <p>对应 {@code application.yml} 中的 {@code open-platform.*} 配置段。Spring Boot 在启动时
 * 通过各字段的 setter 完成绑定，因此新增配置项时只需：</p>
 * <ol>
 *   <li>在下面的静态内部类里加字段 + getter/setter；</li>
 *   <li>在 {@code application.yml} 里写同名的 kebab-case 配置（如 {@code token-ttl}）。</li>
 * </ol>
 *
 * <p>所有时间类配置都使用 {@link Duration}，支持 {@code 15m}、{@code 30s}、{@code 1h} 这种写法。</p>
 */
@ConfigurationProperties(prefix = "open-platform")
public class OpenPlatformProperties {

    /** 认证相关配置：JWT 签发方/受众、有效期、时钟偏移等。 */
    private final Auth auth = new Auth();

    /** 防重放相关配置：时间戳允许偏移、nonce 保存时长。 */
    private final Replay replay = new Replay();

    /** 内部网关调用相关配置：网关地址、连接/读取超时。 */
    private final Internal internal = new Internal();

    /** 开放平台门户（前端控制台 + 门户 API）相关配置。 */
    private final Portal portal = new Portal();

    public Auth getAuth() {
        return auth;
    }

    public Replay getReplay() {
        return replay;
    }

    public Internal getInternal() {
        return internal;
    }

    public Portal getPortal() {
        return portal;
    }

    /**
     * 认证配置。
     *
     * <p>{@code issuer/audience} 是 JWT 的“签发方/受众”，校验时必须完全匹配，防止把别的系统
     * 签发的 token 拿到本平台使用。{@code clockSkew} 用于容忍不同机器之间的少量时间误差。</p>
     */
    public static class Auth {

        /** 外部访问令牌的签发方（iss）。 */
        private String issuer = "open-platform";

        /** 外部访问令牌的受众（aud），代表开放平台对外 API。 */
        private String audience = "open-api";

        /** 外部访问令牌有效期，默认 15 分钟（短 TTL 降低泄露风险）。 */
        private Duration tokenTtl = Duration.ofMinutes(15);

        /** 内部调用令牌的签发方（iss）。 */
        private String internalIssuer = "open-platform";

        /** 内部调用令牌的受众（aud），网关只接受该受众。 */
        private String internalAudience = "internal-gateway";

        /** 内部调用令牌有效期，默认 5 分钟。 */
        private Duration internalTtl = Duration.ofMinutes(5);

        /** 校验 JWT 时间字段时允许的时钟偏移，默认 60 秒。 */
        private Duration clockSkew = Duration.ofSeconds(60);

        /**
         * 是否把 OAuth2 令牌限制为“严格一次性”。
         *
         * <p>默认关闭：同一个 token 在有效期内可以多次调用。打开后，同一个 {@code jti} 只能成功
         * 使用一次，适合高敏感接口，但会降低客户端重试的容错性。</p>
         */
        private boolean jtiOneTime = false;

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public Duration getTokenTtl() { return tokenTtl; }
        public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
        public String getInternalIssuer() { return internalIssuer; }
        public void setInternalIssuer(String internalIssuer) { this.internalIssuer = internalIssuer; }
        public String getInternalAudience() { return internalAudience; }
        public void setInternalAudience(String internalAudience) { this.internalAudience = internalAudience; }
        public Duration getInternalTtl() { return internalTtl; }
        public void setInternalTtl(Duration internalTtl) { this.internalTtl = internalTtl; }
        public Duration getClockSkew() { return clockSkew; }
        public void setClockSkew(Duration clockSkew) { this.clockSkew = clockSkew; }
        public boolean isJtiOneTime() { return jtiOneTime; }
        public void setJtiOneTime(boolean jtiOneTime) { this.jtiOneTime = jtiOneTime; }
    }

    /**
     * 防重放配置。
     *
     * <p>签名模式要求请求头带毫秒时间戳和随机 nonce。时间戳超出 {@code timestampSkew} 直接拒绝；
     * nonce 在 {@code nonceTtl} 内重复出现也拒绝。</p>
     */
    public static class Replay {

        /** 请求时间戳与服务器时间允许的最大偏差，默认 5 分钟。 */
        private Duration timestampSkew = Duration.ofMinutes(5);

        /** nonce 去重记录的保留时长，默认 10 分钟（应大于 timestampSkew）。 */
        private Duration nonceTtl = Duration.ofMinutes(10);

        public Duration getTimestampSkew() { return timestampSkew; }
        public void setTimestampSkew(Duration timestampSkew) { this.timestampSkew = timestampSkew; }
        public Duration getNonceTtl() { return nonceTtl; }
        public void setNonceTtl(Duration nonceTtl) { this.nonceTtl = nonceTtl; }
    }

    /**
     * 内部网关调用配置。
     *
     * <p>开放平台校验完外部请求后，会用内部令牌调用 {@code crcit-gateway} 暴露的固定路由。
     * 超时时间必须显式设置，否则下游故障时线程会被长时间挂住。</p>
     */
    public static class Internal {

        /** 内网网关基础地址，例如 http://localhost:8080。 */
        private String baseUrl = "http://localhost:8080";

        /** 建立连接超时，默认 2 秒。 */
        private Duration connectTimeout = Duration.ofSeconds(2);

        /** 读取响应超时，默认 5 秒。 */
        private Duration readTimeout = Duration.ofSeconds(5);

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    /**
     * 门户配置。
     *
     * <p>门户登录使用独立 JWT（与开放 API 的 token 完全隔离），避免把控制台 token
     * 拿去调用业务 API，或把业务 token 拿来登录控制台。</p>
     */
    public static class Portal {

        /** 门户登录 token 有效期，默认 8 小时。 */
        private Duration tokenTtl = Duration.ofHours(8);

        /** 允许跨域访问门户 API 的前端地址，多个用逗号分隔。 */
        private String allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";

        public Duration getTokenTtl() { return tokenTtl; }
        public void setTokenTtl(Duration tokenTtl) { this.tokenTtl = tokenTtl; }
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
