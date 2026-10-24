package com.crcit.openplatform.internal;

import com.crcit.openplatform.config.OpenPlatformProperties;
import com.crcit.openplatform.oauth.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内部调用令牌的缓存。
 *
 * <p>内部令牌默认只有 5 分钟有效期，没必要每次调用网关都重新签发；这里缓存到“过期前 30 秒”
 * 再刷新。缓存 key 必须包含 {@code apiId + appId + enterpriseId} 三个维度——令牌里带有这些
 * 声明，只用 apiId 做 key 会把 A 应用的令牌发给 B 应用，造成越权。</p>
 *
 * <p>骨架阶段是单机内存缓存；多实例部署时各实例各自签发即可（内部令牌由共享私钥签名），
 * 不需要分布式缓存。</p>
 */
@Service
public class InternalTokenService {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenService.class);

    /** 提前刷新时间，避免刚好卡在过期边界。 */
    private static final long REFRESH_AHEAD_SECONDS = 30;

    private final JwtService jwtService;
    private final OpenPlatformProperties properties;
    private final Map<CacheKey, CachedToken> cache = new ConcurrentHashMap<>();

    public InternalTokenService(JwtService jwtService, OpenPlatformProperties properties) {
        this.jwtService = jwtService;
        this.properties = properties;
    }

    /**
     * 获取内部调用令牌，优先使用缓存。
     */
    public String getToken(String apiId, String appId, String enterpriseId) {
        CacheKey cacheKey = new CacheKey(apiId, appId, enterpriseId);
        CachedToken cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            log.debug("命中内部令牌缓存 apiId={} appId={}", apiId, appId);
            return cached.token();
        }

        String token = jwtService.issueInternalToken(apiId, appId, enterpriseId);
        Instant expiresAt = Instant.now()
                .plus(properties.getAuth().getInternalTtl())
                .minusSeconds(REFRESH_AHEAD_SECONDS);
        cache.put(cacheKey, new CachedToken(token, expiresAt));
        log.debug("刷新内部令牌缓存 apiId={} appId={} enterpriseId={}", apiId, appId, enterpriseId);
        return token;
    }

    /** 主动失效某个租户维度下的内部令牌（例如密钥轮换或应用停用时）。 */
    public void evict(String apiId, String appId, String enterpriseId) {
        cache.remove(new CacheKey(apiId, appId, enterpriseId));
    }

    /** 使用 record 作为 key，天然避免字符串拼接分隔符冲突。 */
    private record CacheKey(String apiId, String appId, String enterpriseId) {
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
