package com.crcit.openplatform.portal;

import com.crcit.openplatform.auth.AuthMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 门户内存数据：登录用户、应用展示信息、接入申请。
 *
 * <p>MVP 用内存实现，重启后回到演示数据；与开放 API 的 {@code InMemoryAppRegistry} 通过
 * {@code appId} 关联。生产环境应拆分为独立表（portal_user / portal_app / access_request）。</p>
 *
 * <p>演示账号（密码只在初始化时哈希，不保存明文）：</p>
 * <ul>
 *   <li>dev@demo.com / Demo@1234（企业开发者，ent-demo）</li>
 *   <li>admin@demo.com / Admin@1234（平台运营，ent-platform）</li>
 * </ul>
 */
@Component
public class PortalStore {

    private static final Logger log = LoggerFactory.getLogger(PortalStore.class);

    private final Map<String, PortalUser> users = new ConcurrentHashMap<>();
    private final Map<String, PortalApp> apps = new ConcurrentHashMap<>();
    private final List<AccessRequest> requests = new CopyOnWriteArrayList<>();

    public PortalStore(PasswordHasher passwordHasher) {
        seedUsers(passwordHasher);
        seedApps();
        seedRequests();
        log.info("门户演示数据初始化完成 users={} apps={} requests={}",
                users.size(), apps.size(), requests.size());
    }

    private void seedUsers(PasswordHasher passwordHasher) {
        users.put("usr_dev", new PortalUser(
                "usr_dev", "张明", "dev@demo.com", "ent-demo",
                PortalRole.DEVELOPER, passwordHasher.hash("Demo@1234"), true));
        users.put("usr_admin", new PortalUser(
                "usr_admin", "李静", "admin@demo.com", "ent-platform",
                PortalRole.ADMIN, passwordHasher.hash("Admin@1234"), true));
        log.info("门户演示账号：dev@demo.com（开发者）、admin@demo.com（平台运营）");
    }

    private void seedApps() {
        Instant now = Instant.now();
        apps.put("app-oauth", new PortalApp(
                "app-oauth", "企业核验服务",
                "OAuth2 接入示例应用，已开通企业基础查询产品。",
                "ent-demo", AuthMode.OAUTH2, now.minus(30, ChronoUnit.DAYS), true));
        apps.put("app-sign", new PortalApp(
                "app-sign", "合作伙伴签名应用",
                "HMAC 签名接入示例应用，已开通企业基础查询产品。",
                "ent-demo", AuthMode.SIGNATURE, now.minus(18, ChronoUnit.DAYS), true));
    }

    private void seedRequests() {
        // 预置一条待审批申请，方便管理员账号一登录就能体验审批流程。
        requests.add(new AccessRequest(
                "req-demo-001", "ent-demo", "app-sign", "enterprise-risk",
                "贷前风控场景需要核验合作企业的风险等级与涉诉信息。",
                RequestStatus.PENDING, Instant.now().minus(2, ChronoUnit.DAYS),
                null, null, null));
    }

    public Optional<PortalUser> findUserByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase();
        return users.values().stream()
                .filter(user -> user.email().equalsIgnoreCase(normalized))
                .findFirst();
    }

    public Optional<PortalUser> findUserById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(users.get(id));
    }

    public Optional<PortalApp> findApp(String appId) {
        return appId == null ? Optional.empty() : Optional.ofNullable(apps.get(appId));
    }

    public List<PortalApp> findAppsByEnterprise(String enterpriseId) {
        return apps.values().stream()
                .filter(app -> app.enterpriseId().equals(enterpriseId))
                .sorted(Comparator.comparing(PortalApp::createdAt).reversed())
                .toList();
    }

    public List<PortalApp> findAllApps() {
        return apps.values().stream()
                .sorted(Comparator.comparing(PortalApp::createdAt).reversed())
                .toList();
    }

    public PortalApp createApp(String appId, String name, String description,
                               String enterpriseId, AuthMode authMode) {
        PortalApp app = new PortalApp(appId, name, description, enterpriseId,
                authMode, Instant.now(), true);
        apps.put(appId, app);
        return app;
    }

    public List<AccessRequest> listRequests() {
        return requests.stream()
                .sorted(Comparator.comparing(AccessRequest::createdAt).reversed())
                .toList();
    }

    public Optional<AccessRequest> findRequest(String id) {
        return requests.stream().filter(request -> request.id().equals(id)).findFirst();
    }

    public boolean hasPendingRequest(String appId, String productCode) {
        return requests.stream().anyMatch(request ->
                request.appId().equals(appId)
                        && request.productCode().equals(productCode)
                        && request.status() == RequestStatus.PENDING);
    }

    public void addRequest(AccessRequest request) {
        requests.add(request);
    }

    public void replaceRequest(AccessRequest request) {
        requests.replaceAll(current -> current.id().equals(request.id()) ? request : current);
    }
}
