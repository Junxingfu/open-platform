package com.crcit.openplatform.common;

import org.slf4j.MDC;

/**
 * 统一响应结构。所有对外接口（包括过滤器里直接写出的错误）都使用该结构：
 *
 * <pre>
 * {
 *   "code": "0",              // 0 表示成功，其余见 ErrorCode
 *   "message": "success",
 *   "requestId": "req_xxx",   // 便于按请求 ID 查日志
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>{@code requestId} 来自 {@link TraceIdFilter} 写入的 MDC，因此在过滤器链之外（例如异步线程）
 * 可能为空。</p>
 */
public record ApiResponse<T>(String code, String message, String requestId, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ErrorCode.SUCCESS.code(), ErrorCode.SUCCESS.message(), currentRequestId(), data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.message());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.code(), message, currentRequestId(), null);
    }

    private static String currentRequestId() {
        return MDC.get("requestId");
    }
}
