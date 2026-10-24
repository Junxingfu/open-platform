package com.crcit.openplatform.web;

import com.crcit.openplatform.common.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公开的健康探测接口，用于确认服务已启动，不需要认证。
 *
 * <p>生产环境通常还会用 Actuator 的 {@code /actuator/health} 做存活/就绪探针；
 * 本接口用于给业务方一个简单的连通性检查入口。</p>
 */
@RestController
public class PingController {

    private static final Logger log = LoggerFactory.getLogger(PingController.class);

    @GetMapping("/open/v1/ping")
    public ApiResponse<Map<String, String>> ping() {
        log.debug("收到 ping 请求");
        return ApiResponse.success(Map.of("status", "UP"));
    }
}
