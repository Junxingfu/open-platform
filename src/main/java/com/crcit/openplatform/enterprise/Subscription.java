package com.crcit.openplatform.enterprise;

import java.time.Instant;

/**
 * 企业订阅关系：企业 + 应用 + API 产品。
 *
 * <p>只有 {@code active=true} 且未过期时，该应用才能调用产品下的 API。</p>
 */
public record Subscription(
        String enterpriseId,
        String appId,
        String productCode,
        Instant expiresAt,
        boolean active
) {
    public boolean usable(Instant now) {
        return active && expiresAt != null && expiresAt.isAfter(now);
    }
}
