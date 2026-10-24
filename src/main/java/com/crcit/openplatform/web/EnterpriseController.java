package com.crcit.openplatform.web;

import com.crcit.openplatform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 示例业务接口：企业信息核验、企业基础信息、企业风险核验。
 *
 * <p>这些路径都已在 {@code InMemoryAppRegistry} 中登记，必须通过认证 + 企业授权后才能进入。
 * 返回内容为演示数据，不代表真实业务结果。</p>
 *
 * <p>三个接口分别属于两个 API 产品：</p>
 * <ul>
 *   <li>企业基础查询（enterprise-basic）：verify、basic；</li>
 *   <li>企业风险核验（enterprise-risk）：risk-check，需要先在门户申请开通。</li>
 * </ul>
 */
@RestController
public class EnterpriseController {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseController.class);

    @PostMapping("/open/v1/enterprise/verify")
    public ApiResponse<Map<String, Object>> verify(@Valid @RequestBody EnterpriseVerifyRequest request) {
        // 能走到这里说明认证、授权、参数校验都已通过。
        // 统一社会信用代码属于敏感信息，日志里做脱敏，避免把完整号码落到日志平台。
        log.info("企业核验请求处理完成 enterpriseName={} creditCode={}",
                request.enterpriseName(), mask(request.creditCode()));
        return ApiResponse.success(Map.of(
                "matched", true,
                "enterpriseName", request.enterpriseName(),
                "creditCode", request.creditCode(),
                "source", "demo"
        ));
    }

    /** 企业基础信息查询（GET，无请求体）。 */
    @GetMapping("/open/v1/enterprise/basic")
    public ApiResponse<Map<String, Object>> basic() {
        log.info("企业基础信息查询请求处理完成");
        return ApiResponse.success(Map.of(
                "enterpriseName", "示例科技集团有限公司",
                "creditCode", "91310000MA1K1234XX",
                "industry", "软件和信息技术服务业",
                "region", "上海市",
                "registeredCapital", "5000 万元人民币",
                "status", "存续",
                "source", "demo"
        ));
    }

    /** 企业风险核验（需要 enterprise.risk scope，对应产品 enterprise-risk）。 */
    @PostMapping("/open/v1/enterprise/risk-check")
    public ApiResponse<Map<String, Object>> riskCheck(@Valid @RequestBody RiskCheckRequest request) {
        log.info("企业风险核验请求处理完成 creditCode={}", mask(request.creditCode()));
        return ApiResponse.success(Map.of(
                "creditCode", request.creditCode(),
                "riskLevel", "LOW",
                "score", 86,
                "checks", List.of(
                        Map.of("item", "经营异常", "result", "PASS"),
                        Map.of("item", "失信被执行", "result", "PASS"),
                        Map.of("item", "股权冻结", "result", "PASS"),
                        Map.of("item", "行政处罚", "result", "REVIEW")
                ),
                "suggestion", "可正常开展合作，建议关注最近 12 个月的行政处罚记录",
                "source", "demo"
        ));
    }

    /** 只保留前 4 位和后 4 位，中间用 * 代替。 */
    private String mask(String value) {
        if (value == null || value.length() <= 8) {
            return "***";
        }
        return value.substring(0, 4) + "**********" + value.substring(value.length() - 4);
    }

    /**
     * 核验请求体。
     *
     * <p>{@code @NotBlank/@Size} 由 spring-boot-starter-validation（Hibernate Validator）执行，
     * 校验失败会被 {@code GlobalExceptionHandler} 转成 400 + 40000 错误码。</p>
     */
    public record EnterpriseVerifyRequest(
            @NotBlank @Size(max = 128) String enterpriseName,
            @NotBlank @Size(min = 18, max = 18) String creditCode
    ) {
    }

    /** 风险核验请求体。 */
    public record RiskCheckRequest(
            @NotBlank @Size(min = 18, max = 18) String creditCode
    ) {
    }
}
