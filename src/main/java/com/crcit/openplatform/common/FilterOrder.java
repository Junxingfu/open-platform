package com.crcit.openplatform.common;

/**
 * 过滤器顺序常量。
 *
 * <p>数字越小越先执行。请求会依次经过：</p>
 * <pre>
 * TraceId(1) → 请求体缓存(2) → 认证方式解析(10) → 门户认证(15)
 *            → OAuth(20)/签名(21) → API 映射(30) → 企业授权(40)
 *            → 业务 Controller → 审计(90)
 * </pre>
 *
 * <p>为什么要显式排序：授权依赖认证写入的 {@code AuthContext}，审计要统计整条链路耗时，
 * 顺序错了会出现“空指针”或“403 但审计没记录”等问题。</p>
 */
public final class FilterOrder {

    public static final int TRACE = 1;
    public static final int REQUEST_CACHE = 2;
    public static final int AUTH_MODE = 10;
    public static final int PORTAL_AUTH = 15;
    public static final int OAUTH = 20;
    public static final int SIGNATURE = 21;
    public static final int API_MAPPING = 30;
    public static final int AUTHORIZATION = 40;
    public static final int AUDIT = 90;

    private FilterOrder() {
    }
}
