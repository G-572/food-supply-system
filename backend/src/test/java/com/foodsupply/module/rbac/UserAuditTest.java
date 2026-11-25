package com.foodsupply.module.rbac;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserAuditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenOf(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", username, "password", password));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private String adminToken() throws Exception {
        return tokenOf("admin", "admin123");
    }

    /** 注册一个新账号，返回它的 id */
    private long register(String username, String appliedRole) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", "abc123",
                "realName", "待审用户",
                "phone", "13900000066",
                "appliedRole", appliedRole));
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        String token = adminToken();
        String response = mockMvc.perform(get("/api/users")
                        .param("keyword", username)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("records").get(0).path("id").asLong();
    }

    @Test
    @DisplayName("审核通过后账号可以登录，角色变成申请的角色")
    void approveThenLogin() throws Exception {
        long id = register("auditpass", "PURCHASER");

        mockMvc.perform(post("/api/users/" + id + "/audit")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "auditpass", "password", "abc123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String roles = objectMapper.readTree(response).path("data").path("user").path("roles").toString();
        org.junit.jupiter.api.Assertions.assertTrue(roles.contains("PURCHASER"));
    }

    @Test
    @DisplayName("驳回后账号无法登录")
    void rejectThenCannotLogin() throws Exception {
        long id = register("auditreject", "PURCHASER");

        mockMvc.perform(post("/api/users/" + id + "/audit")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":false,\"reason\":\"信息不完整\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "auditreject", "password", "abc123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("重复审核返回 409")
    void auditTwice() throws Exception {
        long id = register("audittwice", "SUPPLIER");

        mockMvc.perform(post("/api/users/" + id + "/audit")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"supplierId\":1}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/users/" + id + "/audit")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"supplierId\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("通过供货商申请时必须指定供货商")
    void supplierAuditNeedsBinding() throws Exception {
        long id = register("auditsupplier", "SUPPLIER");

        mockMvc.perform(post("/api/users/" + id + "/audit")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("调整角色后权限立即变化")
    void assignRolesTakesEffect() throws Exception {
        String admin = adminToken();

        // buyer01 原本是采购员，改成供货商角色后不应再能建计划
        mockMvc.perform(put("/api/users/2/roles")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"SUPPLIER\"]}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "buyer01", "password", "buyer123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String perms = objectMapper.readTree(response).path("data").path("user").path("permissions").toString();
        org.junit.jupiter.api.Assertions.assertFalse(perms.contains("plan:send"));
        org.junit.jupiter.api.Assertions.assertTrue(perms.contains("order:confirm"));
    }

    @Test
    @DisplayName("采购员没有审核权限，返回 403")
    void purchaserCannotAudit() throws Exception {
        long id = register("auditforbid", "PURCHASER");

        mockMvc.perform(post("/api/users/" + id + "/audit")
                        .header("Authorization", "Bearer " + tokenOf("buyer01", "buyer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}
