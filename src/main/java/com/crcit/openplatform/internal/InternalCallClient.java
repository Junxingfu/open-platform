package com.crcit.openplatform.internal;

import com.crcit.openplatform.common.BizException;
import com.crcit.openplatform.common.ErrorCode;
import com.crcit.openplatform.config.OpenPlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 内网网关调用客户端。
 *
 * <p>开放平台不直接访问业务系统，而是带“内部调用令牌”调用 {@code crcit-gateway} 的固定路由，
 * 由网关完成服务发现、路由和内部鉴权。</p>
 *
 * <p>安全约定：{@code gatewayPath} 必须来自平台内部路由表，绝不能由外部请求参数拼接，
 * 否则会形成 SSRF/越权跳转。</p>
 */
@Component
public class InternalCallClient {

    private static final Logger log = LoggerFactory.getLogger(InternalCallClient.class);

    private final RestClient restClient;
    private final InternalTokenService tokenService;

    public InternalCallClient(OpenPlatformProperties properties, InternalTokenService tokenService) {
        // 显式设置连接/读取超时：下游网关故障时快速失败，避免 Tomcat 线程被长时间占满。
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getInternal().getConnectTimeout());
        requestFactory.setReadTimeout(properties.getInternal().getReadTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.getInternal().getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.tokenService = tokenService;
        log.info("内部调用客户端初始化完成 baseUrl={} connectTimeout={} readTimeout={}",
                properties.getInternal().getBaseUrl(),
                properties.getInternal().getConnectTimeout(),
                properties.getInternal().getReadTimeout());
    }

    /**
     * 调用内网网关的固定路由。
     *
     * @param gatewayPath 平台内部路由，例如 /internal/open/v1/enterprise/verify
     * @param apiId       被调用的 API ID，写入内部 token
     * @param appId       外部调用方应用 ID
     * @param enterpriseId 外部调用方企业 ID
     * @param requestId   原始请求的 requestId，便于全链路排查
     * @param traceId     原始请求的 traceId
     * @param jsonBody    已校验过的请求体
     */
    public String post(String gatewayPath, String apiId, String appId, String enterpriseId,
                       String requestId, String traceId, String jsonBody) {
        String internalToken = tokenService.getToken(apiId, appId, enterpriseId);
        long start = System.currentTimeMillis();
        try {
            String response = restClient.post()
                    .uri(gatewayPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Internal-Token", internalToken)
                    .header("X-Open-Request-Id", requestId)
                    .header("X-Open-App-Id", appId)
                    .header("X-Open-Api-Id", apiId)
                    .header("X-Open-Trace-Id", traceId)
                    .body(jsonBody == null ? "{}" : jsonBody)
                    .retrieve()
                    .body(String.class);
            log.info("调用内网网关成功 apiId={} path={} costMs={}",
                    apiId, gatewayPath, System.currentTimeMillis() - start);
            return response;
        } catch (RestClientException e) {
            log.error("调用内网网关失败 apiId={} path={} costMs={} error={}",
                    apiId, gatewayPath, System.currentTimeMillis() - start, e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "internal gateway call failed");
        }
    }
}
