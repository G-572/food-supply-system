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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserManageTest {

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

    private String createBody(String username, List<String> roles, Long supplierId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", "new123");
        body.put("realName", "新同事");
        body.put("phone", "13900000055");
        body.put("roleCodes", roles);
        if (supplierId != null) {
            body.put("supplierId", supplierId);
        }
        return objectMapper.writeValueAsString(body);
    }

    @Test
    @DisplayName("管理员创建采购员账号，新号能直接登录")
    void createPurchaser() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("newpurchaser", List.of("PURCHASER"), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNumber());

        tokenOf("newpurchaser", "new123");
    }

    @Test
    @DisplayName("用户名重复返回 409")
    void createDuplicate() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("buyer01", List.of("PURCHASER"), null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("不允许通过接口创建管理员")
    void cannotCreateAdmin() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("newadmin", List.of("ADMIN"), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不支持的角色: ADMIN"));
    }

    @Test
    @DisplayName("供货商账号不绑供货商返回 400")
    void supplierNeedsBinding() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("newsupplier", List.of("SUPPLIER"), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("供货商账号必须绑定一家供货商"));
    }

    @Test
    @DisplayName("停用账号后无法登录")
    void disabledCannotLogin() throws Exception {
        String admin = adminToken();

        String response = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("tobedisabled", List.of("PURCHASER"), null)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long newId = objectMapper.readTree(response).path("data").asLong();

        mockMvc.perform(patch("/api/users/" + newId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "tobedisabled", "password", "new123"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("修改用户基本信息")
    void updateUser() throws Exception {
        String admin = adminToken();

        mockMvc.perform(put("/api/users/2")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realName\":\"张采购改\",\"phone\":\"13800001111\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("采购员不能建号，返回 403")
    void purchaserCannotCreate() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + tokenOf("buyer01", "buyer123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("hacker", List.of("PURCHASER"), null)))
                .andExpect(status().isForbidden());
    }
}
