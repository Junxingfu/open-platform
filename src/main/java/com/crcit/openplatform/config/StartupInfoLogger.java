package com.crcit.openplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动完成后打印一份“人类可读”的运行信息。
 *
 * <p>Spring Boot 自带日志只说明“Started”，新手往往不知道下一步该访问哪个地址。这里统一输出：
 * 端口、健康检查地址、演示接口、JWT 有效期、签名示例脚本位置。</p>
 *
 * <p><b>注意</b>：示例中的 appSecret 仅用于本地演示，生产环境不要打印任何密钥。</p>
 */
@Component
public class StartupInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    private final Environment environment;
    private final OpenPlatformProperties properties;

    public StartupInfoLogger(Environment environment, OpenPlatformProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", "8081"));
        String profiles = environment.getActiveProfiles().length == 0
                ? "default" : String.join(",", environment.getActiveProfiles());

        log.info("============================================================");
        log.info("开放平台启动成功  profile={}  port={}", profiles, port);
        log.info("健康检查   GET  http://127.0.0.1:{}/actuator/health", port);
        log.info("连通性探测 GET  http://127.0.0.1:{}/open/v1/ping", port);
        log.info("OAuth2 令牌 POST http://127.0.0.1:{}/open/oauth2/token", port);
        log.info("示例业务   POST http://127.0.0.1:{}/open/v1/enterprise/verify", port);
        log.info("门户 API   http://127.0.0.1:{}/portal/api", port);
        log.info("门户前端   http://localhost:5173（另开终端 cd frontend && npm run dev）");
        log.info("门户账号   dev@demo.com / Demo@1234（开发者）");
        log.info("           admin@demo.com / Admin@1234（平台运营）");
        log.info("令牌有效期 access={} internal={} clockSkew={}",
                properties.getAuth().getTokenTtl(),
                properties.getAuth().getInternalTtl(),
                properties.getAuth().getClockSkew());
        log.info("演示应用   app-oauth/oauth-secret（OAuth2）, app-sign/sign-secret（HMAC 签名）");
        log.info("签名示例   python3 scripts/demo-signature.py");
        log.info("============================================================");
    }
}
