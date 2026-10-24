package com.crcit.openplatform.auth;

/**
 * 过滤器之间传递数据用的 request attribute key。
 *
 * <p>之所以用 request attribute 而不是 ThreadLocal：一次 HTTP 请求在同一个线程内顺序经过所有
 * 过滤器，request attribute 的生命周期正好与请求一致，也不会像 ThreadLocal 那样忘记清理。</p>
 */
public final class AuthAttributes {

    /** 认证方式（{@link AuthMode}），由 AuthModeResolverFilter 写入。 */
    public static final String AUTH_MODE = "open-platform.auth-mode";

    /** 认证结果（{@link AuthContext}），由 OAuth/签名过滤器写入。 */
    public static final String AUTH_CONTEXT = "open-platform.auth-context";

    /** 命中的 API 定义（{@code ApiDefinition}），由 ApiMappingFilter 写入。 */
    public static final String API_DEFINITION = "open-platform.api-definition";

    private AuthAttributes() {
    }
}
