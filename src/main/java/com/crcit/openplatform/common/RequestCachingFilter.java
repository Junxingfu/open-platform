package com.crcit.openplatform.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求体缓存过滤器。
 *
 * <p>签名模式需要读取 body 计算 SHA-256，Spring MVC 之后还要再读一次，所以这里把 body
 * 包装成 {@link CachedBodyHttpServletRequest}，让后续代码可以重复读取。</p>
 *
 * <p>{@code multipart/form-data}（文件上传）和 {@code application/x-www-form-urlencoded}
 * （OAuth2 令牌请求）不缓存，原因有二：</p>
 * <ul>
 *   <li>文件上传体积可能很大，缓存到内存有 OOM 风险；</li>
 *   <li>表单请求的参数由 Servlet 容器在第一次读取时解析，过滤器提前读流会导致
 *       {@code request.getParameter(...)} 拿不到值。</li>
 * </ul>
 * <p>因此签名模式的接口约定使用 JSON 等原始 body；表单接口请使用 OAuth2 模式。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request instanceof CachedBodyHttpServletRequest || isStreamHandledByContainer(request)) {
            chain.doFilter(request, response);
            return;
        }
        chain.doFilter(new CachedBodyHttpServletRequest(request), response);
    }

    private boolean isStreamHandledByContainer(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        String lower = contentType.toLowerCase();
        return lower.startsWith("multipart/") || lower.startsWith("application/x-www-form-urlencoded");
    }
}
