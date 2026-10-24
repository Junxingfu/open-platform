package com.crcit.openplatform;

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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 门户控制台端到端测试，覆盖完整接入闭环：
 *
 * <ol>
 *   <li>开发者登录，浏览 API 目录/产品；</li>
 *   <li>创建应用，拿到 AppKey/AppSecret；</li>
 *   <li>提交 API 产品接入申请；</li>
 *   <li>管理员登录并审批通过；</li>
 *   <li>开发者用新应用换取 OAuth2 token，调用刚开通的风险接口。</li>
 * </ol>
 *
 * <p>同时验证未登录访问门户 API 会返回 401。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PortalFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void portalApiShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/portal/api/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("40100"));
    }

    @Test
    void developerCanBrowseCatalogAndProducts() throws Exception {
        String token = login("dev@demo.com", "Demo@1234");

        mockMvc.perform(get("/portal/api/apis").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.length()").value(3));

        mockMvc.perform(get("/portal/api/apis/enterprise.risk-check")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.apiId").value("enterprise.risk-check"))
                .andExpect(jsonPath("$.data.rateLimit").isNotEmpty());

        mockMvc.perform(get("/portal/api/products").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").isNotEmpty());
    }

    @Test
    void fullAccessRequestFlowShouldGrantProductAndAllowApiCall() throws Exception {
        String devToken = login("dev@demo.com", "Demo@1234");

        // 1. 创建应用
        MvcResult created = mockMvc.perform(post("/portal/api/apps")
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "门户联调应用",
                                "description", "PortalFlowTest 创建",
                                "authMode", "OAUTH2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.app.appId").isNotEmpty())
                .andExpect(jsonPath("$.data.appSecret").isNotEmpty())
                .andReturn();
        JsonNode createdJson = read(created);
        String appId = createdJson.at("/data/app/appId").asText();
        String appSecret = createdJson.at("/data/appSecret").asText();

        // 2. 提交接入申请（企业风险核验产品）
        MvcResult requestResult = mockMvc.perform(post("/portal/api/access-requests")
                        .header("Authorization", bearer(devToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "appId", appId,
                                "productCode", "enterprise-risk",
                                "useCase", "贷前风控场景需要核验企业风险"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String requestId = read(requestResult).at("/data/id").asText();

        // 3. 管理员登录并审批
        String adminToken = login("admin@demo.com", "Admin@1234");
        mockMvc.perform(post("/portal/api/access-requests/" + requestId + "/decision")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "APPROVE",
                                "comment", "资料完整，同意接入"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewer").value("李静"));

        // 4. 用新应用换 OAuth2 token
        MvcResult tokenResult = mockMvc.perform(post("/open/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", appId)
                        .param("client_secret", appSecret)
                        .param("scope", "enterprise.risk"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andReturn();
        String accessToken = read(tokenResult).at("/access_token").asText();

        // 5. 调用刚开通的风险接口
        mockMvc.perform(post("/open/v1/enterprise/risk-check")
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"creditCode\":\"91310000MA1K1234XX\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.riskLevel").value("LOW"));
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/portal/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email,
                                "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        return read(result).at("/data/token").asText();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
