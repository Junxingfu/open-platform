package com.crcit.openplatform;

import com.crcit.openplatform.signature.SignatureService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端接口测试，覆盖两条认证链路：
 *
 * <ol>
 *   <li>OAuth2：拿 token → 带 Bearer token 调业务接口；</li>
 *   <li>HMAC 签名：计算 Content-SHA256 + HMAC → 调业务接口 → 重复 nonce 被拒。</li>
 * </ol>
 *
 * <p>这些测试不监听真实端口（MockMvc），因此可以直接在 IDEA 里运行，不会和 8081 端口冲突。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenPlatformFlowTest {

    private static final String API_PATH = "/open/v1/enterprise/verify";
    private static final String BODY =
            "{\"enterpriseName\":\"示例科技有限公司\",\"creditCode\":\"91310000MA1K1234XX\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignatureService signatureService;

    @Test
    void pingShouldBePublic() throws Exception {
        mockMvc.perform(get("/open/v1/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    void requestWithoutCredentialShouldReturn401() throws Exception {
        mockMvc.perform(post(API_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"));
    }

    @Test
    void oauth2TokenThenCallApiShouldSucceed() throws Exception {
        String token = issueOAuthToken();

        mockMvc.perform(post(API_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.matched").value(true))
                .andExpect(jsonPath("$.data.enterpriseName").value("示例科技有限公司"));
    }

    @Test
    void invalidBodyShouldReturn400() throws Exception {
        String token = issueOAuthToken();

        mockMvc.perform(post(API_PATH)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enterpriseName\":\"\",\"creditCode\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("40000"));
    }

    @Test
    void signatureRequestShouldSucceedAndReplayShouldBeRejected() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String contentSha = signatureService.sha256Hex(body);
        String canonical = signatureService.canonicalString("POST", API_PATH, null, timestamp, nonce, body);
        String signature = signatureService.signHmacSha256("sign-secret", canonical);

        // 第一次：签名、摘要、时间戳、nonce 全部合法。
        mockMvc.perform(post(API_PATH)
                        .header("X-App-Id", "app-sign")
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", nonce)
                        .header("X-Signature-Method", "HMAC-SHA256")
                        .header("X-Signature", signature)
                        .header("Content-SHA256", contentSha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        // 第二次：nonce 已被登记，必须按重放拒绝。
        mockMvc.perform(post(API_PATH)
                        .header("X-App-Id", "app-sign")
                        .header("X-Timestamp", timestamp)
                        .header("X-Nonce", nonce)
                        .header("X-Signature-Method", "HMAC-SHA256")
                        .header("X-Signature", signature)
                        .header("Content-SHA256", contentSha)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40104"));
    }

    /** 复用 OAuth2 令牌端点，返回 access_token。 */
    private String issueOAuthToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/open/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "app-oauth")
                        .param("client_secret", "oauth-secret")
                        .param("scope", "enterprise.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return json.get("access_token").asText();
    }
}
