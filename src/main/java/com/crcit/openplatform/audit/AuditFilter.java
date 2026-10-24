package com.crcit.openplatform.audit;

import com.crcit.openplatform.auth.AuthAttributes;
import com.crcit.openplatform.auth.AuthContext;
import com.crcit.openplatform.common.FilterOrder;
import com.crcit.openplatform.enterprise.ApiDefinition;
import com.crcit.openplatform.portal.UsageTracker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 审计过滤器：无论请求成功还是失败，最后都会输出一条结构化审计日志。
 *
 * <p>日志 logger 名为 {@code OPEN_PLATFORM_AUDIT}，可在日志平台单独采集。字段包括：
 * 应用、企业、API、方法、路径、HTTP 状态、耗时、traceId。</p>
 *
 * <p>同时把已认证的开放 API 调用写入 {@link UsageTracker}，供门户的调用统计使用。
 * 门户请求（{@code /portal/**}）没有开放 API 的 {@code AuthContext}，不会被计入 API 用量。</p>
 *
 * <p>放在过滤器链最后（{@code @Order(90)}），因此能统计到认证/授权过滤器的耗时；
 * 又因为它在 {@link com.crcit.openplatform.common.TraceIdFilter} 内部，finally 执行时
 * MDC 里的 traceId 还没有被清理。</p>
 */
@Component
@Order(FilterOrder.AUDIT)
public class AuditFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("OPEN_PLATFORM_AUDIT");

    private final UsageTracker usageTracker;

    public AuditFilter(UsageTracker usageTracker) {
        this.usageTracker = usageTracker;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            long costMs = System.currentTimeMillis() - start;
            AuthContext context = (AuthContext) request.getAttribute(AuthAttributes.AUTH_CONTEXT);
            ApiDefinition api = (ApiDefinition) request.getAttribute(AuthAttributes.API_DEFINITION);
            audit.info("app={} enterprise={} api={} method={} path={} status={} costMs={} requestId={} traceId={}",
                    context == null ? "-" : context.appId(),
                    context == null ? "-" : context.enterpriseId(),
                    api == null ? "-" : api.apiId(),
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    costMs,
                    MDC.get("requestId"),
                    MDC.get("traceId"));
            usageTracker.record(context, api, response.getStatus(), costMs);
        }
    }
}
