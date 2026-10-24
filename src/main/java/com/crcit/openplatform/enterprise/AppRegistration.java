package com.crcit.openplatform.enterprise;

import com.crcit.openplatform.auth.AuthMode;

import java.util.Set;

/**
 * 应用注册信息（一个企业可以创建多个应用）。
 *
 * <p><b>安全提示</b>：骨架阶段 {@code appSecret} 是明文，仅用于演示。生产环境必须改为
 * 加密存储/加盐哈希，并支持密钥轮换。</p>
 *
 * @param appId         应用 ID，即 OAuth2 的 client_id
 * @param appSecret     应用密钥，即 OAuth2 的 client_secret / 签名模式的 HMAC 密钥
 * @param enterpriseId  所属企业
 * @param authMode      该应用启用的认证方式
 * @param active        应用是否启用
 * @param productCodes  已订阅的 API 产品
 */
public record AppRegistration(
        String appId,
        String appSecret,
        String enterpriseId,
        AuthMode authMode,
        boolean active,
        Set<String> productCodes
) {
}
