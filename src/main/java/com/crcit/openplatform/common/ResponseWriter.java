package com.crcit.openplatform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 直接向 HttpServletResponse 写 JSON 的底层工具。
 *
 * <p>统一设置 UTF-8，避免中文错误信息乱码；Content-Type 固定为 {@code application/json}。</p>
 */
public final class ResponseWriter {

    private ResponseWriter() {
    }

    public static void write(HttpServletResponse response, int status, ApiResponse<?> body, ObjectMapper mapper) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}
