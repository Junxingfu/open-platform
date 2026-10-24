package com.crcit.openplatform.common;

/**
 * 业务异常。
 *
 * <p>业务代码只负责抛出“错误码 + 可读原因”，由 {@link GlobalExceptionHandler} 或各过滤器
 * 统一转换成 HTTP 状态码和 {@link ApiResponse}。这样控制器里不需要写 try/catch。</p>
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
