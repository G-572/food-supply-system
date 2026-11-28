package com.foodsupply.module.supplier;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SupplierControllerTest {

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
    @DisplayName("管理员分页查供货商，种子数据 3 家")
    void adminCanList() throws Exception {
        mockMvc.perform(get("/api/suppliers")
                        .param("pageNum", "1")
                        .param("pageSize", "10")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].supplierId").doesNotExist());
    }

    @Test
    @DisplayName("供货商账号也能查列表，它自己有 supplier:read")
    void supplierCanList() throws Exception {
        mockMvc.perform(get("/api/suppliers")
                        .header("Authorization", "Bearer " + tokenOf("supplier01", "supplier123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
    }

    @Test
    @DisplayName("采购员没有 supplier:create，新增被拒 403")
    void purchaserCannotCreate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "测试供货商"));

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenOf("buyer01", "buyer123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("未登录查供货商返回 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/suppliers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("按名称关键词过滤")
    void filterByKeyword() throws Exception {
        mockMvc.perform(get("/api/suppliers")
                        .param("keyword", "蔬菜")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("绿源蔬菜基地"));
    }

    @Test
    @DisplayName("名称重复时返回 409")
    void duplicateName() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "绿源蔬菜基地"));

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("供货商下还有商品时不能删")
    void cannotDeleteSupplierWithProducts() throws Exception {
        String list = mockMvc.perform(get("/api/suppliers")
                        .param("keyword", "鲜丰")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(list).path("data").path("records").get(0).path("id").asLong();

        mockMvc.perform(delete("/api/suppliers/" + id)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("商品")));
    }

    @Test
    @DisplayName("新建空供货商后可以删掉")
    void createThenDelete() throws Exception {
        String token = adminToken();
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "临时供货商", "contactPerson", "测试", "phone", "0571-88886666"));

        String response = mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long id = objectMapper.readTree(response).path("data").asLong();

        mockMvc.perform(delete("/api/suppliers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
