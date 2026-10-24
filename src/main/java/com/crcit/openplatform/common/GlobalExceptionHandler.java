package com.crcit.openplatform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器：把异常统一转换成 {@link ApiResponse}，避免每个 Controller 重复写 try/catch。
 *
 * <p>处理优先级：更具体的异常类型优先匹配；最后的 {@link Exception} 是兜底，会把完整堆栈打到
 * ERROR 日志，方便排查未知问题。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务主动抛出的异常（如 token 过期、scope 不足）。 */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException e) {
        if (e.getErrorCode() == ErrorCode.INTERNAL_ERROR) {
            log.error("业务处理失败 code={} message={}", e.getErrorCode().code(), e.getMessage(), e);
        } else {
            // 客户端错误很常见（如凭证过期），用 debug 避免刷屏；需要排查时打开该包 DEBUG 日志即可。
            log.debug("业务请求被拒绝 code={} message={}", e.getErrorCode().code(), e.getMessage());
        }
        return ResponseEntity.status(statusOf(e.getErrorCode()))
                .body(ApiResponse.failure(e.getErrorCode(), e.getMessage()));
    }

    /** @Valid 校验请求体失败（字段为空、长度不合法等）。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.debug("请求参数校验失败: {}", message);
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.PARAM_INVALID, message));
    }

    /** 缺少必填查询参数/表单参数，例如 OAuth2 少了 client_id。 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "缺少参数: " + e.getParameterName();
        log.debug(message);
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.PARAM_INVALID, message));
    }

    /** 参数类型不匹配，例如把字符串传给 long 类型的 timestamp。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数类型错误: " + e.getName();
        log.debug(message);
        return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.PARAM_INVALID, message));
    }

    /** 请求体不是合法 JSON，或 JSON 结构与 DTO 不匹配。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        log.debug("请求体解析失败: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.PARAM_INVALID, "请求体格式错误，需为合法 JSON"));
    }

    /** HTTP 方法不支持，例如用 GET 调 POST 接口。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.failure(ErrorCode.METHOD_NOT_ALLOWED, e.getMessage()));
    }

    /** Content-Type 不支持，例如令牌端点了非表单请求。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.failure(ErrorCode.UNSUPPORTED_MEDIA_TYPE, e.getMessage()));
    }

    /** 访问了不存在的路径（Spring 6.1+ 静态资源未找到异常）。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(ErrorCode.API_NOT_FOUND, "path not found"));
    }

    /** 兜底：未预期的异常一律 500，并记录完整堆栈。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未处理的异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(ErrorCode.INTERNAL_ERROR));
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    /** 错误码 → HTTP 状态码。前 3 位与 HTTP 状态码保持一致，便于前端统一处理。 */
    private HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case PARAM_INVALID -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED, TOKEN_INVALID, SIGNATURE_INVALID, TIMESTAMP_INVALID, NONCE_REPLAY -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case API_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
