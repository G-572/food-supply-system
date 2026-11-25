package com.foodsupply.module.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String loginAndGetToken() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "buyer01",
                "password", "buyer123"));

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    @Test
    @DisplayName("不带 Token 访问 /auth/me 返回 401")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录后能拿到自己的信息，退出后同一 Token 立刻失效")
    void logoutInvalidatesToken() throws Exception {
        String token = loginAndGetToken();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("buyer01"))
                .andExpect(jsonPath("$.data.permissions").isNotEmpty());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("伪造的 Token 被拒")
    void forgedTokenRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("弱密码注册返回 400 并给出提示")
    void weakPasswordRejected() throws Exception {
        String body = """
                {"username":"weakuser","password":"123456","realName":"弱密码",
                 "phone":"13900000098","appliedRole":"PURCHASER"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("字母和数字")));
    }

    @Test
    @DisplayName("待审核账号登录返回 403")
    void pendingUserLoginRejected() throws Exception {
        String registerBody = """
                {"username":"pending01","password":"abc123","realName":"待审核",
                 "phone":"13900000097","appliedRole":"SUPPLIER"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isOk());

        String loginBody = objectMapper.writeValueAsString(Map.of(
                "username", "pending01",
                "password", "abc123"));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isForbidden());
    }
}
