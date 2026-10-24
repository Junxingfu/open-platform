package com.crcit.openplatform.portal;

/**
 * 从门户 JWT 解析出来的当前登录人。
 *
 * <p>由 {@link PortalAuthFilter} 写入 request attribute，控制器通过
 * {@link PortalControllerSupport#principal} 获取。</p>
 */
public record PortalPrincipal(
        String userId,
        String name,
        String email,
        String enterpriseId,
        PortalRole role
) {
    public boolean isAdmin() {
        return role == PortalRole.ADMIN;
    }
}
