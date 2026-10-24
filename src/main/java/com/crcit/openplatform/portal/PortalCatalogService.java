package com.crcit.openplatform.portal;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.enterprise.ApiDefinition;
import com.crcit.openplatform.enterprise.ApiProduct;
import com.crcit.openplatform.enterprise.AppRegistry;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 门户 API 目录服务。
 *
 * <p>把开放 API 的“运行定义”（{@link ApiDefinition}，用于鉴权）和“文档内容”
 * （本类里的 {@link ApiDoc}，用于展示）组合成门户需要的摘要/详情。</p>
 *
 * <p>MVP 文档写在代码里，保证与演示接口一致；后续可迁移到数据库或文档平台，
 * 但对外 DTO 结构保持不变。</p>
 */
@Service
public class PortalCatalogService {

    private final AppRegistry registry;
    private final PortalStore portalStore;
    private final Map<String, ApiDoc> docs = new LinkedHashMap<>();
    private final Map<String, String> productDescriptions = new LinkedHashMap<>();

    public PortalCatalogService(AppRegistry registry, PortalStore portalStore) {
        this.registry = registry;
        this.portalStore = portalStore;
        seedDocs();
    }

    /** 目录搜索：关键字匹配名称/摘要/路径，可按产品、HTTP 方法过滤。 */
    public List<PortalViews.ApiSummary> search(String keyword, String productCode, String method) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String product = productCode == null ? "" : productCode.trim();
        String httpMethod = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);

        return registry.listApis().stream()
                .filter(api -> product.isEmpty() || api.productCode().equals(product))
                .filter(api -> httpMethod.isEmpty() || api.method().equalsIgnoreCase(httpMethod))
                .map(api -> summaryOf(api, docs.get(api.apiId())))
                .filter(summary -> kw.isEmpty()
                        || summary.name().toLowerCase(Locale.ROOT).contains(kw)
                        || summary.summary().toLowerCase(Locale.ROOT).contains(kw)
                        || summary.path().toLowerCase(Locale.ROOT).contains(kw)
                        || summary.apiId().toLowerCase(Locale.ROOT).contains(kw))
                .sorted(Comparator.comparing(PortalViews.ApiSummary::category)
                        .thenComparing(PortalViews.ApiSummary::name))
                .toList();
    }

    public PortalViews.ApiDetail detail(String apiId) {
        ApiDefinition api = registry.listApis().stream()
                .filter(item -> item.apiId().equals(apiId))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.API_NOT_FOUND, "API 不存在"));
        ApiDoc doc = docs.getOrDefault(api.apiId(), fallbackDoc(api));
        List<PortalViews.ApiSummary> related = doc.relatedApiIds().stream()
                .map(this::findSummary)
                .flatMap(Optional::stream)
                .toList();
        return new PortalViews.ApiDetail(
                summaryOf(api, doc),
                doc.description(),
                doc.authModes(),
                doc.rateLimit(),
                doc.headers(),
                doc.params(),
                doc.requestExample(),
                doc.responseExample(),
                doc.errors(),
                related);
    }

    /** 产品列表，附带当前企业已开通的应用数、待审批申请数。 */
    public List<PortalViews.ProductView> products(String enterpriseId) {
        List<PortalApp> apps = portalStore.findAppsByEnterprise(enterpriseId);
        List<AccessRequest> requests = portalStore.listRequests().stream()
                .filter(request -> request.enterpriseId().equals(enterpriseId))
                .toList();
        return registry.listProducts().stream()
                .map(product -> new PortalViews.ProductView(
                        product.code(),
                        product.name(),
                        productDescriptions.getOrDefault(product.code(), "API 产品"),
                        product.scopes().stream().sorted().toList(),
                        product.apiIds().size(),
                        product.active(),
                        (int) apps.stream()
                                .filter(app -> registry.hasActiveSubscription(
                                        enterpriseId, app.appId(), product.code()))
                                .count(),
                        (int) requests.stream()
                                .filter(request -> request.productCode().equals(product.code())
                                        && request.status() == RequestStatus.PENDING)
                                .count()))
                .toList();
    }

    public String apiName(String apiId) {
        ApiDoc doc = docs.get(apiId);
        if (doc != null) {
            return doc.name();
        }
        return registry.listApis().stream()
                .filter(api -> api.apiId().equals(apiId))
                .map(ApiDefinition::apiId)
                .findFirst()
                .orElse(apiId);
    }

    public int apiCount() {
        return registry.listApis().size();
    }

    private Optional<PortalViews.ApiSummary> findSummary(String apiId) {
        return registry.listApis().stream()
                .filter(api -> api.apiId().equals(apiId))
                .findFirst()
                .map(api -> summaryOf(api, docs.get(api.apiId())));
    }

    private PortalViews.ApiSummary summaryOf(ApiDefinition api, ApiDoc doc) {
        ApiProduct product = registry.findProduct(api.productCode()).orElse(null);
        return new PortalViews.ApiSummary(
                api.apiId(),
                doc == null ? api.apiId() : doc.name(),
                api.method(),
                api.path(),
                doc == null ? "暂无详细文档" : doc.summary(),
                api.productCode(),
                product == null ? api.productCode() : product.name(),
                api.requiredScope(),
                doc == null ? "未分类" : doc.category(),
                doc == null ? "v1" : doc.version(),
                doc == null ? List.of("OAUTH2", "SIGNATURE") : doc.authModes(),
                "已上线");
    }

    private ApiDoc fallbackDoc(ApiDefinition api) {
        return new ApiDoc(api.apiId(), "暂无详细文档", "暂无详细文档",
                "未分类", "v1", List.of("OAUTH2", "SIGNATURE"), "以平台公告为准",
                List.of(), List.of(), "{}", "{}", List.of(), List.of());
    }

    private void seedDocs() {
        productDescriptions.put("enterprise-basic",
                "企业工商信息、基础画像与实名核验能力，适合供应商准入、客户尽调、开户审核等场景。");
        productDescriptions.put("enterprise-risk",
                "企业风险等级、司法涉诉、经营异常与行政处罚核验，适合贷前风控、合作方风险评估。");

        List<PortalViews.HeaderDoc> commonHeaders = List.of(
                new PortalViews.HeaderDoc("Content-Type", true, "固定为 application/json", "application/json"),
                new PortalViews.HeaderDoc("Authorization", false,
                        "OAuth2 模式必填：Bearer <access_token>", "Bearer eyJhbGciOi..."),
                new PortalViews.HeaderDoc("X-App-Id", false, "签名模式必填：应用 ID", "app-sign"),
                new PortalViews.HeaderDoc("X-Timestamp", false, "签名模式必填：毫秒时间戳", "1793851200000"),
                new PortalViews.HeaderDoc("X-Nonce", false, "签名模式必填：一次性随机串", "9f2c..."),
                new PortalViews.HeaderDoc("X-Signature-Method", false, "签名模式固定为 HMAC-SHA256", "HMAC-SHA256"),
                new PortalViews.HeaderDoc("X-Signature", false, "签名模式必填：Base64 编码的 HMAC", "3q2+7w..."),
                new PortalViews.HeaderDoc("Content-SHA256", false,
                        "签名模式必填：请求体 SHA-256 小写十六进制", "e3b0c442...")
        );

        List<PortalViews.ErrorDoc> commonErrors = List.of(
                new PortalViews.ErrorDoc("40000", "parameter invalid", "请求参数缺失或格式错误"),
                new PortalViews.ErrorDoc("40100", "unauthorized", "未携带认证凭证"),
                new PortalViews.ErrorDoc("40101", "token invalid", "token 无效、过期或被撤销"),
                new PortalViews.ErrorDoc("40102", "signature invalid", "签名或请求体摘要不一致"),
                new PortalViews.ErrorDoc("40103", "timestamp invalid", "时间戳超出允许窗口"),
                new PortalViews.ErrorDoc("40300", "forbidden", "应用/企业停用、未订阅产品或 scope 不足"),
                new PortalViews.ErrorDoc("40400", "api not found", "接口未登记或路径错误")
        );

        docs.put("enterprise.verify", new ApiDoc(
                "企业信息核验",
                "根据企业名称与统一社会信用代码核验企业身份是否匹配。",
                "适用于供应商准入、客户开户、合同签署前的企业实名核验。平台会校验企业名称与统一社会信用代码是否一致，"
                        + "并返回演示用的匹配结果。生产环境将对接工商/征信数据源。",
                "企业服务",
                "v1",
                List.of("OAUTH2", "SIGNATURE"),
                "10 QPS / 应用，单日上限 10,000 次（演示值）",
                commonHeaders,
                List.of(
                        new PortalViews.ParamDoc("enterpriseName", "body", "string", true,
                                "企业全称，最长 128 个字符", "示例科技有限公司"),
                        new PortalViews.ParamDoc("creditCode", "body", "string", true,
                                "统一社会信用代码，固定 18 位", "91310000MA1K1234XX")),
                """
                        {
                          "enterpriseName": "示例科技有限公司",
                          "creditCode": "91310000MA1K1234XX"
                        }
                        """,
                """
                        {
                          "code": "0",
                          "message": "success",
                          "requestId": "req_8bd6ee91e884433e",
                          "data": {
                            "matched": true,
                            "enterpriseName": "示例科技有限公司",
                            "creditCode": "91310000MA1K1234XX",
                            "source": "demo"
                          }
                        }
                        """,
                commonErrors,
                List.of("enterprise.basic")));

        docs.put("enterprise.basic", new ApiDoc(
                "企业基础信息查询",
                "查询企业工商基础信息、行业、注册地与经营状态。",
                "适合供应商档案补全、客户画像、开户资料预填等场景。该接口为 GET，无请求体；"
                        + "返回内容为演示数据，接入真实数据源后字段结构保持不变。",
                "企业服务",
                "v1",
                List.of("OAUTH2", "SIGNATURE"),
                "20 QPS / 应用（演示值）",
                commonHeaders,
                List.of(),
                "GET /open/v1/enterprise/basic",
                """
                        {
                          "code": "0",
                          "message": "success",
                          "requestId": "req_xxx",
                          "data": {
                            "enterpriseName": "示例科技集团有限公司",
                            "creditCode": "91310000MA1K1234XX",
                            "industry": "软件和信息技术服务业",
                            "region": "上海市",
                            "registeredCapital": "5000 万元人民币",
                            "status": "存续",
                            "source": "demo"
                          }
                        }
                        """,
                commonErrors,
                List.of("enterprise.verify")));

        docs.put("enterprise.risk-check", new ApiDoc(
                "企业风险核验",
                "核验企业经营异常、失信被执行、股权冻结与行政处罚等风险项。",
                "适合贷前风控、合作方准入、招投标资格审查等场景。该接口属于“企业风险核验”产品，"
                        + "需要先在门户提交接入申请，审批通过后应用才会获得 enterprise.risk scope。",
                "风控服务",
                "v1",
                List.of("OAUTH2", "SIGNATURE"),
                "5 QPS / 应用（演示值）",
                commonHeaders,
                List.of(
                        new PortalViews.ParamDoc("creditCode", "body", "string", true,
                                "统一社会信用代码，固定 18 位", "91310000MA1K1234XX")),
                """
                        {
                          "creditCode": "91310000MA1K1234XX"
                        }
                        """,
                """
                        {
                          "code": "0",
                          "message": "success",
                          "requestId": "req_xxx",
                          "data": {
                            "creditCode": "91310000MA1K1234XX",
                            "riskLevel": "LOW",
                            "score": 86,
                            "checks": [
                              {"item": "经营异常", "result": "PASS"},
                              {"item": "失信被执行", "result": "PASS"},
                              {"item": "股权冻结", "result": "PASS"},
                              {"item": "行政处罚", "result": "REVIEW"}
                            ],
                            "suggestion": "可正常开展合作，建议关注最近 12 个月的行政处罚记录",
                            "source": "demo"
                          }
                        }
                        """,
                commonErrors,
                List.of("enterprise.verify")));
    }

    /** 单个 API 的文档内容（内部结构）。 */
    private record ApiDoc(
            String name,
            String summary,
            String description,
            String category,
            String version,
            List<String> authModes,
            String rateLimit,
            List<PortalViews.HeaderDoc> headers,
            List<PortalViews.ParamDoc> params,
            String requestExample,
            String responseExample,
            List<PortalViews.ErrorDoc> errors,
            List<String> relatedApiIds
    ) {
    }
}
