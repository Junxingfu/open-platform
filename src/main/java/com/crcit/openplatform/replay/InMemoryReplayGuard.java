package com.crcit.openplatform.replay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防重放的内存实现（骨架阶段）。
 *
 * <p>用一张 {@code key → 过期时间} 的表保存三类记录：</p>
 * <ul>
 *   <li>{@code nonce:appId:nonce}：签名请求的随机数，防止同一请求重复提交；</li>
 *   <li>{@code jti-once:jti}：严格一次性 token 的已用记录；</li>
 *   <li>{@code jti-revoked:jti}：被主动撤销的 token。</li>
 * </ul>
 *
 * <p>生产环境应替换为 Redis（带 TTL 和原子 SETNX），接口保持不变即可平滑替换。
 * 内存实现只适合单实例部署：多实例之间不共享记录。</p>
 */
@Component
public class InMemoryReplayGuard implements ReplayGuard {

    private static final Logger log = LoggerFactory.getLogger(InMemoryReplayGuard.class);

    private final Map<String, Instant> values = new ConcurrentHashMap<>();

    @Override
    public boolean isJtiRevoked(String jti) {
        return valid("jti-revoked:" + jti);
    }

    @Override
    public void revokeJti(String jti, Duration ttl) {
        values.put("jti-revoked:" + jti, Instant.now().plus(ttl));
        log.debug("已撤销 jti={} ttl={}", jti, ttl);
    }

    @Override
    public boolean markJtiOnce(String jti, Duration ttl) {
        return putIfAbsent("jti-once:" + jti, ttl);
    }

    @Override
    public boolean markNonce(String appId, String nonce, Duration ttl) {
        return putIfAbsent("nonce:" + appId + ":" + nonce, ttl);
    }

    /**
     * 原子写入：只有 key 不存在或已过期时才成功。
     *
     * <p>注意过期分支用 {@code replace(key, existing, expiresAt)}，避免两个线程同时发现过期时
     * 互相覆盖对方刚写入的新值。</p>
     */
    private boolean putIfAbsent(String key, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        Instant existing = values.putIfAbsent(key, expiresAt);
        if (existing == null) {
            return true;
        }
        if (existing.isBefore(now)) {
            return values.replace(key, existing, expiresAt);
        }
        return false;
    }

    private boolean valid(String key) {
        Instant expiresAt = values.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(Instant.now())) {
            values.remove(key, expiresAt);
            return false;
        }
        return true;
    }

    /**
     * 定时清理过期记录，防止长期运行后内存无限增长。
     *
     * <p>默认每 60 秒执行一次；如果要调整，可把 fixedDelay 改成读取配置。</p>
     */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void cleanExpired() {
        Instant now = Instant.now();
        int before = values.size();
        values.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - values.size();
        if (removed > 0) {
            log.debug("防重放表清理完成 removed={} remaining={}", removed, values.size());
        }
    }
}
