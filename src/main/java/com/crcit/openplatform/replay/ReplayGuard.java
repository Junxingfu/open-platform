package com.crcit.openplatform.replay;

import java.time.Duration;

/**
 * 防重放能力接口。
 *
 * <p>把接口与实现分离（当前是内存实现，生产可换 Redis），业务代码只依赖接口，
 * 替换存储时不需要改过滤器。</p>
 */
public interface ReplayGuard {

    /** 判断某个 JWT 的 jti 是否已被撤销。 */
    boolean isJtiRevoked(String jti);

    /** 主动撤销某个 JWT（例如用户退出登录）。 */
    void revokeJti(String jti, Duration ttl);

    /**
     * 登记“一次性 token 已使用”。
     *
     * @return true 表示这是第一次使用
     */
    boolean markJtiOnce(String jti, Duration ttl);

    /**
     * 登记签名请求的 nonce。
     *
     * @return true 表示这是第一次出现
     */
    boolean markNonce(String appId, String nonce, Duration ttl);
}
