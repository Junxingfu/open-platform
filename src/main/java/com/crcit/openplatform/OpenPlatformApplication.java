package com.crcit.openplatform;

import com.crcit.openplatform.config.OpenPlatformProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 开放平台启动类（整个服务的入口）。
 *
 * <p>启动过程大致分为四步：</p>
 * <ol>
 *   <li>Spring Boot 扫描 {@code com.crcit.openplatform} 下的 {@code @Component/@Service/@RestController}；</li>
 *   <li>把 {@code application.yml} 中 {@code open-platform.*} 的配置绑定到 {@link OpenPlatformProperties}；</li>
 *   <li>启动内嵌 Tomcat（默认 8081 端口），并按 {@code @Order} 顺序注册各个过滤器；</li>
 *   <li>初始化内存演示数据（企业、应用、API 产品、订阅）和本地 RSA 密钥。</li>
 * </ol>
 *
 * <p>新手排查启动问题时，先看控制台最后 30 行：最常见的原因是 8081 端口被占用、JDK 不是 17，
 * 或者 IDEA 没有成功导入 Maven 依赖（表现为大量“找不到符号/程序包不存在”）。</p>
 */
@EnableCaching
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(OpenPlatformProperties.class)
public class OpenPlatformApplication {

    private static final Logger log = LoggerFactory.getLogger(OpenPlatformApplication.class);

    public static void main(String[] args) {
        try {
            SpringApplication.run(OpenPlatformApplication.class, args);
        } catch (Exception e) {
            // 兜底打印，避免启动失败时只看到一大段堆栈却不知道先查什么。
            log.error("开放平台启动失败：请优先检查 8081 端口是否被占用、JDK 是否为 17、"
                    + "以及 Maven 依赖是否已完整下载（IDEA 右侧 Maven 面板点击 Reload）。", e);
            throw e;
        }
    }
}
