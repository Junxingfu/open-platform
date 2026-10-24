package com.crcit.openplatform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 过滤器专用的错误输出工具。
 *
 * <p>过滤器在进入 Spring MVC 之前就可能拒绝请求（如 token 无效），此时 {@code @RestControllerAdvice}
 * 还没机会生效，所以需要直接往响应里写 JSON。格式与正常接口保持一致，前端无需特殊处理。</p>
 */
public final class FilterError {

    private FilterError() {
    }

    public static void write(HttpServletResponse response, ObjectMapper mapper,
                             int status, ErrorCode code, String message) throws IOException {
        ResponseWriter.write(response, status, ApiResponse.failure(code, message), mapper);
    }
}
