package com.crcit.openplatform.auth;

/**
 * 平台支持的两种外部认证方式。
 *
 * <p>同一个应用只启用其中一种（由应用注册信息决定），认证方式由请求头推断：</p>
 * <ul>
 *   <li>{@link #OAUTH2}：携带 {@code Authorization: Bearer &lt;jwt&gt;}；</li>
 *   <li>{@link #SIGNATURE}：携带 {@code X-App-Id} 及签名相关请求头。</li>
 * </ul>
 */
public enum AuthMode {
    OAUTH2,
    SIGNATURE
}
