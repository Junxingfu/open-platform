package com.crcit.openplatform.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 令牌响应体，字段名遵循 RFC 6749：
 * {@code access_token/token_type/expires_in/scope}。
 *
 * <p>注意：令牌本身只在响应里返回一次，服务端不落库、不打印日志。</p>
 */
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("scope") String scope
) {
}
