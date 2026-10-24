package com.crcit.openplatform.portal;

/**
 * 开放平台门户用户。
 *
 * <p>与开放 API 的“应用”不同：门户用户是登录控制台的人，应用是真正调用 API 的凭证载体。
 * 一个企业可以有多个门户用户和多个应用。</p>
 *
 * @param passwordHash PBKDF2 编码后的密码，绝不保存明文
 */
public record PortalUser(
        String id,
        String name,
        String email,
        String enterpriseId,
        PortalRole role,
        String passwordHash,
        boolean active
) {
}
