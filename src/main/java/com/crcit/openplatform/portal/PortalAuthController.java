package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.ApiResponse;
import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 门户登录/当前用户/退出接口。
 *
 * <p>登录接口是 {@code /portal/api/**} 下唯一的公开接口，由 {@code PortalAuthFilter}
 * 通过路径精确放行。</p>
 */
@RestController
@RequestMapping("/portal/api/auth")
public class PortalAuthController {

    private static final Logger log = LoggerFactory.getLogger(PortalAuthController.class);

    private final PortalStore portalStore;
    private final PasswordHasher passwordHasher;
    private final PortalTokenService tokenService;
    private final PortalDashboardService dashboardService;

    public PortalAuthController(PortalStore portalStore, PasswordHasher passwordHasher,
                                PortalTokenService tokenService, PortalDashboardService dashboardService) {
        this.portalStore = portalStore;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
        this.dashboardService = dashboardService;
    }

    @PostMapping("/login")
    public ApiResponse<PortalViews.LoginResult> login(
            @Valid @RequestBody PortalRequests.LoginRequest request) {
        PortalUser user = portalStore.findUserByEmail(request.email())
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误"));
        if (!user.active() || !passwordHasher.matches(request.password(), user.passwordHash())) {
            log.warn("门户登录失败 email={}", request.email());
            throw new BizException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        String token = tokenService.issue(user);
        PortalPrincipal principal = toPrincipal(user);
        log.info("门户登录成功 userId={} role={}", user.id(), user.role());
        return ApiResponse.success(new PortalViews.LoginResult(
                token, dashboardService.userView(principal)));
    }

    @GetMapping("/me")
    public ApiResponse<PortalViews.UserView> me(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.userView(
                PortalControllerSupport.principal(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, String>> logout() {
        // 门户 token 是无状态 JWT，MVP 由前端删除本地 token；如需强制失效可接入 jti 黑名单。
        return ApiResponse.success(Map.of("message", "已退出登录"));
    }

    private PortalPrincipal toPrincipal(PortalUser user) {
        return new PortalPrincipal(user.id(), user.name(), user.email(),
                user.enterpriseId(), user.role());
    }
}
