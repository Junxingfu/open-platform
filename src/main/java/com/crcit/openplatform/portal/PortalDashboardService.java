package com.crcit.openplatform.portal;

import com.crcit.openplatform.enterprise.AppRegistry;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 门户首页聚合服务。
 *
 * <p>把应用、申请、调用量、目录数量组合成首页需要的统计数据与引导步骤。
 * 开发者只看本企业；管理员看全平台，并额外看到待审批数。</p>
 */
@Service
public class PortalDashboardService {

    private final AppRegistry registry;
    private final PortalStore portalStore;
    private final PortalCatalogService catalogService;
    private final PortalUsageService usageService;

    public PortalDashboardService(AppRegistry registry,
                                  PortalStore portalStore,
                                  PortalCatalogService catalogService,
                                  PortalUsageService usageService) {
        this.registry = registry;
        this.portalStore = portalStore;
        this.catalogService = catalogService;
        this.usageService = usageService;
    }

    public PortalViews.DashboardView dashboard(PortalPrincipal principal) {
        boolean admin = principal.isAdmin();
        String enterpriseId = principal.enterpriseId();
        List<PortalApp> apps = admin
                ? portalStore.findAllApps()
                : portalStore.findAppsByEnterprise(enterpriseId);

        Set<String> approvedProducts = new HashSet<>();
        for (PortalApp app : apps) {
            for (String productCode : registry.findApp(app.appId())
                    .map(registration -> registration.productCodes())
                    .orElse(Set.of())) {
                if (registry.hasActiveSubscription(app.enterpriseId(), app.appId(), productCode)) {
                    approvedProducts.add(productCode);
                }
            }
        }

        List<AccessRequest> requests = portalStore.listRequests().stream()
                .filter(request -> admin || request.enterpriseId().equals(enterpriseId))
                .toList();
        int pendingRequests = (int) requests.stream()
                .filter(request -> request.status() == RequestStatus.PENDING)
                .count();
        int pendingApprovals = admin ? pendingRequests : 0;

        PortalViews.UsageView usage = usageService.summary(enterpriseId, 7, admin);
        PortalViews.UserView user = userView(principal);

        List<PortalViews.OnboardingStep> onboarding = List.of(
                new PortalViews.OnboardingStep(
                        "create-app", "创建应用",
                        "应用是调用开放 API 的身份载体，创建后可获得 AppKey/AppSecret。",
                        !apps.isEmpty(), "/apps"),
                new PortalViews.OnboardingStep(
                        "request-api", "申请 API 产品",
                        "选择应用与 API 产品提交接入申请，平台运营审批后开通调用权限。",
                        !approvedProducts.isEmpty(), "/requests"),
                new PortalViews.OnboardingStep(
                        "first-call", "完成首次调用",
                        "按接入指南换取 OAuth2 token 或计算 HMAC 签名，调用第一个接口。",
                        usage.totalCalls() > 0, "/guide"));

        List<PortalViews.Announcement> announcements = List.of(
                new PortalViews.Announcement("ann-1", "企业风险核验 API 正式上线",
                        "新增 enterprise-risk 产品，覆盖经营异常、失信被执行、股权冻结等风险项，欢迎申请接入。",
                        "2026-10-20", "新接口"),
                new PortalViews.Announcement("ann-2", "沙箱环境维护通知",
                        "本周日 02:00-04:00 沙箱环境例行维护，期间调用可能出现短暂抖动，请提前安排。",
                        "2026-10-18", "公告"),
                new PortalViews.Announcement("ann-3", "接入指南已更新",
                        "补充 HMAC-SHA256 签名示例与常见错误码说明，建议重新阅读接入指南。",
                        "2026-10-16", "文档"));

        PortalViews.Stats stats = new PortalViews.Stats(
                apps.size(),
                approvedProducts.size(),
                pendingRequests,
                usage.totalCalls(),
                usage.successRate(),
                usage.avgLatencyMs(),
                catalogService.apiCount(),
                pendingApprovals);

        return new PortalViews.DashboardView(
                user, stats, onboarding, usage.recent().stream().limit(6).toList(), announcements);
    }

    public PortalViews.UserView userView(PortalPrincipal principal) {
        String enterpriseName = registry.findEnterprise(principal.enterpriseId())
                .map(enterprise -> enterprise.name())
                .orElse(principal.enterpriseId());
        return new PortalViews.UserView(
                principal.userId(),
                principal.name(),
                principal.email(),
                principal.role().name(),
                principal.enterpriseId(),
                enterpriseName);
    }
}
