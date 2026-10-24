package com.crcit.openplatform.auth;

import java.util.Set;

/**
 * 认证成功后得到的上下文，后续授权和审计都从这里取信息。
 *
 * @param enterpriseId 企业 ID（企业级授权的核心维度）
 * @param appId        应用 ID
 * @param authMode     本次请求使用的认证方式
 * @param scopes       该应用被授予的权限点集合
 * @param jti          OAuth2 令牌唯一 ID；签名模式为 null
 */
public record AuthContext(
        String enterpriseId,
        String appId,
        AuthMode authMode,
        Set<String> scopes,
        String jti
) {
}
