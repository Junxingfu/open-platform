package com.crcit.openplatform;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文启动冒烟测试：只要能加载完整 Spring 上下文，就说明 Bean 依赖、配置绑定、
 * 过滤器注册没有明显问题。
 *
 * <p>接口级联调测试见 {@link OpenPlatformFlowTest}。</p>
 */
@SpringBootTest
class OpenPlatformApplicationTests {

    @Test
    void contextLoads() {
        // 空方法即可：上下文加载失败会自动让测试失败。
    }
}
