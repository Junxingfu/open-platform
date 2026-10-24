package com.crcit.openplatform.portal;

import com.crcit.openplatform.auth.AuthMode;

import java.time.Instant;

/**
 * 门户视角的应用。
 *
 * <p>真正的密钥和订阅关系保存在 {@code AppRegistration}/{@code Subscription} 中；
 * 本记录只保存门户展示需要的名称、描述和创建时间，避免把展示字段混进开放 API 的核心模型。</p>
 */
public record PortalApp(
        String appId,
        String name,
        String description,
        String enterpriseId,
        AuthMode authMode,
        Instant createdAt,
        boolean active
) {
}
