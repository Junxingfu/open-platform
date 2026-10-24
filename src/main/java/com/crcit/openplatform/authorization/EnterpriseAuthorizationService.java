package com.crcit.openplatform.authorization;

import com.crcit.openplatform.auth.AuthContext;
import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.enterprise.ApiDefinition;
import com.crcit.openplatform.enterprise.ApiProduct;
import com.crcit.openplatform.enterprise.AppRegistration;
import com.crcit.openplatform.enterprise.AppRegistry;
import com.crcit.openplatform.enterprise.Enterprise;
import org.springframework.stereotype.Service;

/**
 * 企业级授权规则（本平台不做企业内的“用户级”授权）。
 *
 * <p>一次请求要同时满足以下条件才放行：</p>
 * <ol>
 *   <li>应用存在且启用；</li>
 *   <li>企业存在且启用；</li>
 *   <li>API 属于某个启用中的 API 产品；</li>
 *   <li>该企业 + 应用 + 产品存在未过期的订阅；</li>
 *   <li>认证上下文里的 scope 包含 API 要求的 scope。</li>
 * </ol>
 *
 * <p>骨架阶段数据来自 {@code InMemoryAppRegistry}，后续替换为数据库实现时本类不需要改。</p>
 */
@Service
public class EnterpriseAuthorizationService {

    private final AppRegistry registry;

    public EnterpriseAuthorizationService(AppRegistry registry) {
        this.registry = registry;
    }

    /**
     * 校验失败时抛 {@link BizException}（错误码 FORBIDDEN），由调用方转成 403。
     */
    public void authorize(AuthContext context, ApiDefinition api) {
        AppRegistration app = registry.findApp(context.appId())
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "app not found"));
        if (!app.active()) {
            throw new BizException(ErrorCode.FORBIDDEN, "app disabled");
        }
        Enterprise enterprise = registry.findEnterprise(context.enterpriseId())
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "enterprise not found"));
        if (!enterprise.active()) {
            throw new BizException(ErrorCode.FORBIDDEN, "enterprise disabled");
        }

        // API 必须属于一个启用中的产品，防止“产品下线但 API 还能调”。
        ApiProduct product = registry.findProduct(api.productCode())
                .orElseThrow(() -> new BizException(ErrorCode.FORBIDDEN, "api product not found"));
        if (!product.active() || !product.apiIds().contains(api.apiId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "api not in product");
        }

        // 订阅是“企业 + 应用 + 产品”维度的，缺一不可。
        if (!registry.hasActiveSubscription(context.enterpriseId(), context.appId(), product.code())) {
            throw new BizException(ErrorCode.FORBIDDEN, "subscription inactive");
        }

        // 最后检查权限点。OAuth2 的 scope 来自 token，签名模式来自应用已订阅产品的 scope。
        if (!context.scopes().contains(api.requiredScope())) {
            throw new BizException(ErrorCode.FORBIDDEN, "scope missing: " + api.requiredScope());
        }
    }
}
