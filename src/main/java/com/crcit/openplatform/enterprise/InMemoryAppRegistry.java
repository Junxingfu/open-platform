package com.crcit.openplatform.enterprise;

import com.crcit.openplatform.auth.AuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 企业/应用/API 产品的内存实现，启动时写入一组演示数据。
 *
 * <p>演示数据：</p>
 * <table border="1">
 *   <tr><th>应用</th><th>密钥</th><th>认证方式</th><th>企业</th><th>已开通产品</th></tr>
 *   <tr><td>app-oauth</td><td>oauth-secret</td><td>OAuth2</td><td>ent-demo</td><td>enterprise-basic</td></tr>
 *   <tr><td>app-sign</td><td>sign-secret</td><td>HMAC 签名</td><td>ent-demo</td><td>enterprise-basic</td></tr>
 * </table>
 *
 * <p>除了只读查询，还实现 {@link MutableAppRegistry}，供开放平台门户创建应用、轮换密钥、
 * 审批后开通产品。生产环境替换为 MySQL/Redis 实现，保持接口不变即可。</p>
 *
 * <p>注意：订阅到期时间按“启动时间 + 365 天”计算，重启后会顺延，仅用于本地演示。</p>
 */
@Component
public class InMemoryAppRegistry implements MutableAppRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAppRegistry.class);

    private final Map<String, Enterprise> enterprises = new ConcurrentHashMap<>();
    private final Map<String, AppRegistration> apps = new ConcurrentHashMap<>();
    private final Map<String, ApiProduct> products = new ConcurrentHashMap<>();
    private final Map<String, ApiDefinition> apis = new ConcurrentHashMap<>();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public InMemoryAppRegistry() {
        seed();
    }

    private void seed() {
        enterprises.put("ent-demo", new Enterprise("ent-demo", "示例科技集团有限公司", true));
        enterprises.put("ent-platform", new Enterprise("ent-platform", "开放平台运营中心", true));

        Set<String> basicProducts = Set.of("enterprise-basic");
        apps.put("app-oauth", new AppRegistration(
                "app-oauth", "oauth-secret", "ent-demo", AuthMode.OAUTH2, true, basicProducts));
        apps.put("app-sign", new AppRegistration(
                "app-sign", "sign-secret", "ent-demo", AuthMode.SIGNATURE, true, basicProducts));

        // 产品一：企业基础查询（已给演示应用开通）
        products.put("enterprise-basic", new ApiProduct(
                "enterprise-basic",
                "企业基础查询",
                new LinkedHashSet<>(List.of("enterprise.read")),
                new LinkedHashSet<>(List.of("enterprise.verify", "enterprise.basic")),
                true));

        // 产品二：企业风险核验（演示应用尚未开通，走门户申请 -> 审批 -> 开通流程）
        products.put("enterprise-risk", new ApiProduct(
                "enterprise-risk",
                "企业风险核验",
                new LinkedHashSet<>(List.of("enterprise.risk")),
                new LinkedHashSet<>(List.of("enterprise.risk-check")),
                true));

        // key 使用 "METHOD path" 的形式，findApi 查询时保持一致。
        apis.put("POST /open/v1/enterprise/verify", new ApiDefinition(
                "enterprise.verify", "POST", "/open/v1/enterprise/verify",
                "enterprise-basic", "enterprise.read"));
        apis.put("GET /open/v1/enterprise/basic", new ApiDefinition(
                "enterprise.basic", "GET", "/open/v1/enterprise/basic",
                "enterprise-basic", "enterprise.read"));
        apis.put("POST /open/v1/enterprise/risk-check", new ApiDefinition(
                "enterprise.risk-check", "POST", "/open/v1/enterprise/risk-check",
                "enterprise-risk", "enterprise.risk"));

        Instant expiresAt = Instant.now().plus(365, ChronoUnit.DAYS);
        subscriptions.add(new Subscription("ent-demo", "app-oauth", "enterprise-basic", expiresAt, true));
        subscriptions.add(new Subscription("ent-demo", "app-sign", "enterprise-basic", expiresAt, true));

        log.info("演示数据初始化完成 enterprises={} apps={} products={} apis={} subscriptions={}",
                enterprises.size(), apps.size(), products.size(), apis.size(), subscriptions.size());
        log.info("演示应用：app-oauth/oauth-secret（OAuth2），app-sign/sign-secret（HMAC 签名）");
    }

    @Override
    public Optional<AppRegistration> findApp(String appId) {
        // ConcurrentHashMap 不允许 null key，这里统一返回 empty，避免调用方拿到 NPE。
        return appId == null ? Optional.empty() : Optional.ofNullable(apps.get(appId));
    }

    @Override
    public Optional<Enterprise> findEnterprise(String enterpriseId) {
        return enterpriseId == null ? Optional.empty() : Optional.ofNullable(enterprises.get(enterpriseId));
    }

    @Override
    public Optional<ApiProduct> findProduct(String productCode) {
        return productCode == null ? Optional.empty() : Optional.ofNullable(products.get(productCode));
    }

    @Override
    public Optional<ApiDefinition> findApi(String method, String path) {
        if (method == null || path == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(apis.get(method + " " + path));
    }

    @Override
    public boolean hasActiveSubscription(String enterpriseId, String appId, String productCode) {
        Instant now = Instant.now();
        return subscriptions.stream()
                .anyMatch(s -> s.enterpriseId().equals(enterpriseId)
                        && s.appId().equals(appId)
                        && s.productCode().equals(productCode)
                        && s.usable(now));
    }

    @Override
    public Set<String> scopesForApp(String appId) {
        AppRegistration app = apps.get(appId);
        if (app == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String productCode : app.productCodes()) {
            ApiProduct product = products.get(productCode);
            if (product != null && product.active()) {
                result.addAll(product.scopes());
            }
        }
        return result;
    }

    @Override
    public List<ApiProduct> listProducts() {
        return products.values().stream()
                .sorted(Comparator.comparing(ApiProduct::code))
                .toList();
    }

    @Override
    public List<ApiDefinition> listApis() {
        return apis.values().stream()
                .sorted(Comparator.comparing(ApiDefinition::apiId))
                .toList();
    }

    @Override
    public AppRegistration registerApp(String appId, String appSecret, String enterpriseId, AuthMode authMode) {
        AppRegistration registration = new AppRegistration(
                appId, appSecret, enterpriseId, authMode, true, Set.of());
        apps.put(appId, registration);
        log.info("注册新应用 appId={} enterpriseId={} authMode={}", appId, enterpriseId, authMode);
        return registration;
    }

    @Override
    public Optional<AppRegistration> updateSecret(String appId, String newSecret) {
        AppRegistration current = apps.get(appId);
        if (current == null) {
            return Optional.empty();
        }
        AppRegistration updated = new AppRegistration(
                current.appId(), newSecret, current.enterpriseId(),
                current.authMode(), current.active(), current.productCodes());
        apps.put(appId, updated);
        log.info("轮换应用密钥 appId={}", appId);
        return Optional.of(updated);
    }

    @Override
    public boolean grantProduct(String appId, String productCode) {
        AppRegistration app = apps.get(appId);
        ApiProduct product = products.get(productCode);
        if (app == null || product == null || !product.active()) {
            return false;
        }
        // 已开通则直接返回成功，审批接口天然幂等。
        if (hasActiveSubscription(app.enterpriseId(), appId, productCode)) {
            return true;
        }
        Set<String> productCodes = new LinkedHashSet<>(app.productCodes());
        productCodes.add(productCode);
        apps.put(appId, new AppRegistration(
                app.appId(), app.appSecret(), app.enterpriseId(),
                app.authMode(), app.active(), productCodes));
        subscriptions.add(new Subscription(
                app.enterpriseId(), appId, productCode,
                Instant.now().plus(365, ChronoUnit.DAYS), true));
        log.info("开通产品 appId={} productCode={}", appId, productCode);
        return true;
    }
}
