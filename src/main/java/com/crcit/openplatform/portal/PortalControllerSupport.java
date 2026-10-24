package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 门户控制器公共方法。
 */
public final class PortalControllerSupport {

    private PortalControllerSupport() {
    }

    /** 取出当前登录人；正常情况一定存在，不存在说明过滤器被绕过。 */
    public static PortalPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(PortalAttributes.PRINCIPAL);
        if (value instanceof PortalPrincipal principal) {
            return principal;
        }
        throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录状态已失效");
    }

    public static void requireAdmin(PortalPrincipal principal) {
        if (!principal.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅平台运营可执行该操作");
        }
    }
}
