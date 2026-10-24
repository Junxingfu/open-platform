package com.crcit.openplatform.portal;

import com.crcit.openplatform.auth.AuthMode;
import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.enterprise.AppRegistration;
import com.crcit.openplatform.enterprise.MutableAppRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 门户应用管理：创建应用、查看/轮换密钥、列出企业应用。
 *
 * <p>密钥策略：</p>
 * <ul>
 *   <li>创建/轮换时返回一次完整 secret，前端提示用户立即保存；</li>
 *   <li>列表只返回掩码；</li>
 *   <li>“查看密钥”需要再次点击，后端返回完整值（MVP 便于联调，生产应加二次认证/审计）。</li>
 * </ul>
 */
@Service
public class PortalAppService {

    private static final Logger log = LoggerFactory.getLogger(PortalAppService.class);

    private final MutableAppRegistry registry;
    private final PortalStore portalStore;

    public PortalAppService(MutableAppRegistry registry, PortalStore portalStore) {
        this.registry = registry;
        this.portalStore = portalStore;
    }

    public List<PortalViews.AppView> list(PortalPrincipal principal) {
        List<PortalApp> apps = principal.isAdmin()
                ? portalStore.findAllApps()
                : portalStore.findAppsByEnterprise(principal.enterpriseId());
        return apps.stream().map(this::toView).toList();
    }

    public PortalViews.AppCreatedView create(PortalPrincipal principal, String name,
                                             String description, String authModeValue) {
        AuthMode authMode = parseAuthMode(authModeValue);
        String appId = generateAppId();
        String appSecret = generateSecret();
        registry.registerApp(appId, appSecret, principal.enterpriseId(), authMode);
        PortalApp app = portalStore.createApp(
                appId,
                name.trim(),
                description == null ? "" : description.trim(),
                principal.enterpriseId(),
                authMode);
        log.info("门户创建应用 userId={} appId={} authMode={}", principal.userId(), appId, authMode);
        return new PortalViews.AppCreatedView(toView(app), appSecret);
    }

    public PortalViews.CredentialsView credentials(PortalPrincipal principal, String appId) {
        PortalApp app = ownedApp(principal, appId);
        AppRegistration registration = registry.findApp(appId)
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "应用不存在"));
        return new PortalViews.CredentialsView(appId, registration.appSecret(), app.authMode().name());
    }

    public PortalViews.CredentialsView rotateSecret(PortalPrincipal principal, String appId) {
        PortalApp app = ownedApp(principal, appId);
        String newSecret = generateSecret();
        AppRegistration updated = registry.updateSecret(appId, newSecret)
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "应用不存在"));
        log.info("门户轮换密钥 userId={} appId={}", principal.userId(), appId);
        return new PortalViews.CredentialsView(appId, updated.appSecret(), app.authMode().name());
    }

    /** 校验应用存在且属于当前企业；管理员可以查看全部。 */
    PortalApp ownedApp(PortalPrincipal principal, String appId) {
        PortalApp app = portalStore.findApp(appId)
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "应用不存在"));
        if (!principal.isAdmin() && !app.enterpriseId().equals(principal.enterpriseId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该应用");
        }
        return app;
    }

    public PortalViews.AppView toView(PortalApp app) {
        AppRegistration registration = registry.findApp(app.appId()).orElse(null);
        List<String> productCodes = registration == null
                ? List.of()
                : registration.productCodes().stream().sorted().toList();
        List<String> productNames = productCodes.stream()
                .map(code -> registry.findProduct(code).map(product -> product.name()).orElse(code))
                .toList();
        String secret = registration == null ? "" : registration.appSecret();
        String enterpriseName = registry.findEnterprise(app.enterpriseId())
                .map(enterprise -> enterprise.name())
                .orElse(app.enterpriseId());
        return new PortalViews.AppView(
                app.appId(),
                app.name(),
                app.description(),
                app.enterpriseId(),
                enterpriseName,
                app.authMode().name(),
                app.active(),
                app.createdAt(),
                maskSecret(secret),
                productCodes,
                productNames);
    }

    private AuthMode parseAuthMode(String value) {
        try {
            return AuthMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "认证方式仅支持 OAUTH2 或 SIGNATURE");
        }
    }

    private String generateAppId() {
        return "app_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String generateSecret() {
        return "sk_" + UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "******";
        }
        if (secret.length() <= 8) {
            return "******";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
