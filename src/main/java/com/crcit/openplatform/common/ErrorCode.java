package com.crcit.openplatform.common;

/**
 * 平台错误码。
 *
 * <p>约定：{@code "0"} 表示成功；其余为 5 位字符串，前 3 位对应 HTTP 状态码，后 2 位是同类错误
 * 的序号。例如 {@code 40103} 对应 HTTP 401，表示时间戳非法。新增错误码时记得在
 * {@link GlobalExceptionHandler#statusOf(ErrorCode)} 里补充 HTTP 状态映射。</p>
 */
public enum ErrorCode {

    SUCCESS("0", "success"),
    PARAM_INVALID("40000", "parameter invalid"),
    UNAUTHORIZED("40100", "unauthorized"),
    TOKEN_INVALID("40101", "token invalid"),
    SIGNATURE_INVALID("40102", "signature invalid"),
    TIMESTAMP_INVALID("40103", "timestamp invalid"),
    NONCE_REPLAY("40104", "nonce replayed"),
    FORBIDDEN("40300", "forbidden"),
    API_NOT_FOUND("40400", "api not found"),
    METHOD_NOT_ALLOWED("40500", "method not allowed"),
    IDEMPOTENCY_CONFLICT("40900", "idempotency conflict"),
    UNSUPPORTED_MEDIA_TYPE("41500", "unsupported media type"),
    RATE_LIMITED("42900", "rate limited"),
    INTERNAL_ERROR("50000", "internal error");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
