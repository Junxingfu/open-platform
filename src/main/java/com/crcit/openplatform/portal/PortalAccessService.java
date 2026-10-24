package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.enterprise.MutableAppRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 接入申请服务：开发者提交“应用 + API 产品”的接入申请，平台运营审批。
 *
 * <p>审批通过后调用 {@link MutableAppRegistry#grantProduct} 创建订阅；
 * 订阅生效后，应用才能获取对应 scope，调用该产品下的 API。</p>
 */
@Service
public class PortalAccessService {

    private static final Logger log = LoggerFactory.getLogger(PortalAccessService.class);

    private final MutableAppRegistry registry;
    private final PortalStore portalStore;

    public PortalAccessService(MutableAppRegistry registry, PortalStore portalStore) {
        this.registry = registry;
        this.portalStore = portalStore;
    }

    public List<PortalViews.AccessRequestView> list(PortalPrincipal principal) {
        return portalStore.listRequests().stream()
                .filter(request -> principal.isAdmin()
                        || request.enterpriseId().equals(principal.enterpriseId()))
                .map(this::toView)
                .toList();
    }

    public PortalViews.AccessRequestView create(PortalPrincipal principal,
                                                String appId, String productCode, String useCase) {
        PortalApp app = portalStore.findApp(appId)
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "应用不存在"));
        if (!principal.isAdmin() && !app.enterpriseId().equals(principal.enterpriseId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权为该应用提交申请");
        }
        var product = registry.findProduct(productCode)
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "API 产品不存在"));
        if (!product.active()) {
            throw new BizException(ErrorCode.FORBIDDEN, "该产品已下线");
        }
        if (registry.hasActiveSubscription(app.enterpriseId(), appId, productCode)) {
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "该应用已开通此产品");
        }
        if (portalStore.hasPendingRequest(appId, productCode)) {
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "该应用已有一条待审批申请");
        }

        AccessRequest request = new AccessRequest(
                "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                app.enterpriseId(),
                appId,
                productCode,
                useCase.trim(),
                RequestStatus.PENDING,
                Instant.now(),
                null,
                null,
                null);
        portalStore.addRequest(request);
        log.info("提交接入申请 userId={} appId={} productCode={}",
                principal.userId(), appId, productCode);
        return toView(request);
    }

    public PortalViews.AccessRequestView decide(PortalPrincipal principal, String requestId,
                                                String decision, String comment) {
        PortalControllerSupport.requireAdmin(principal);
        AccessRequest request = portalStore.findRequest(requestId)
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "申请不存在"));
        if (request.status() != RequestStatus.PENDING) {
            throw new BizException(ErrorCode.IDEMPOTENCY_CONFLICT, "该申请已处理");
        }

        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        RequestStatus next;
        if ("APPROVE".equals(normalized)) {
            next = RequestStatus.APPROVED;
            boolean granted = registry.grantProduct(request.appId(), request.productCode());
            if (!granted) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "开通产品失败，请检查应用与产品状态");
            }
        } else if ("REJECT".equals(normalized)) {
            next = RequestStatus.REJECTED;
        } else {
            throw new BizException(ErrorCode.PARAM_INVALID, "审批结果仅支持 APPROVE 或 REJECT");
        }

        AccessRequest updated = request.decide(next, principal.name(),
                comment == null ? "" : comment.trim());
        portalStore.replaceRequest(updated);
        log.info("审批接入申请 requestId={} decision={} reviewer={}",
                requestId, next, principal.userId());
        return toView(updated);
    }

    private PortalViews.AccessRequestView toView(AccessRequest request) {
        String appName = portalStore.findApp(request.appId())
                .map(PortalApp::name)
                .orElse(request.appId());
        String productName = registry.findProduct(request.productCode())
                .map(product -> product.name())
                .orElse(request.productCode());
        String enterpriseName = registry.findEnterprise(request.enterpriseId())
                .map(enterprise -> enterprise.name())
                .orElse(request.enterpriseId());
        return new PortalViews.AccessRequestView(
                request.id(),
                request.enterpriseId(),
                enterpriseName,
                request.appId(),
                appName,
                request.productCode(),
                productName,
                request.useCase(),
                request.status().name(),
                request.createdAt(),
                request.decidedAt(),
                request.reviewer(),
                request.reviewComment());
    }
}
