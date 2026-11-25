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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleControllerTest {

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

    @Test
    @DisplayName("角色列表带权限码和使用人数")
    void listRoles() throws Exception {
        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].roleCode").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].permissionCodes.length()").value(34))
                .andExpect(jsonPath("$.data[0].userCount").value(1));
    }

    @Test
    @DisplayName("权限字典共 34 条")
    void listPermissions() throws Exception {
        mockMvc.perform(get("/api/permissions").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(34));
    }

    @Test
    @DisplayName("新建角色编码重复返回 409")
    void createDuplicateRole() throws Exception {
        mockMvc.perform(post("/api/roles")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCode\":\"ADMIN\",\"roleName\":\"重复角色\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("内置管理员角色不能删除")
    void cannotDeleteAdminRole() throws Exception {
        mockMvc.perform(delete("/api/roles/1").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("内置管理员角色不可删除"));
    }

    @Test
    @DisplayName("角色下还有账号时不能删除")
    void cannotDeleteRoleInUse() throws Exception {
        mockMvc.perform(delete("/api/roles/3").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("给采购员角色去掉 plan:send 后，该权限立即失效")
    void revokePermissionTakesEffect() throws Exception {
        String admin = adminToken();

        // 先查出 PURCHASER 当前的权限 ID，去掉 plan:send 对应的那个
        String permsResponse = mockMvc.perform(get("/api/permissions")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var perms = objectMapper.readTree(permsResponse).path("data");
        long planSendId = -1;
        var ids = new java.util.ArrayList<Long>();
        for (var p : perms) {
            if ("plan:send".equals(p.path("permCode").asText())) {
                planSendId = p.path("id").asLong();
            } else {
                ids.add(p.path("id").asLong());
            }
        }
        assertTrue(planSendId > 0);

        String body = objectMapper.writeValueAsString(Map.of("permissionIds", ids));
        mockMvc.perform(put("/api/roles/2/permissions")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        String loginResponse = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "buyer01", "password", "buyer123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String permissions = objectMapper.readTree(loginResponse)
                .path("data").path("user").path("permissions").toString();
        assertFalse(permissions.contains("plan:send"));
        assertTrue(permissions.contains("plan:create"));
    }

    @Test
    @DisplayName("采购员访问角色管理返回 403")
    void purchaserForbidden() throws Exception {
        mockMvc.perform(get("/api/roles").header("Authorization", "Bearer " + tokenOf("buyer01", "buyer123")))
                .andExpect(status().isForbidden());
    }
}
