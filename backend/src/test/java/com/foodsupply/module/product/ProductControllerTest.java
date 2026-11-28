package com.foodsupply.module.product;

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
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProductControllerTest {

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

    private String supplier01Token() throws Exception {
        return tokenOf("supplier01", "supplier123");
    }

    /** 按关键词定位一条商品，返回 [id, supplierId] */
    private long[] findProduct(String token, String keyword) throws Exception {
        String response = mockMvc.perform(get("/api/products")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var first = objectMapper.readTree(response).path("data").path("records").get(0);
        return new long[]{first.path("id").asLong(), first.path("supplierId").asLong()};
    }

    private Map<String, Object> productBody(String name, Object supplierId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("category", "蔬菜");
        body.put("unit", "斤");
        body.put("price", 4.50);
        body.put("stockQuantity", 100);
        if (supplierId != null) {
            body.put("supplierId", supplierId);
        }
        return body;
    }

    @Test
    @DisplayName("管理员能看全部 8 个商品，列表带供货商名称")
    void adminListsAll() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("pageSize", "20")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(8))
                .andExpect(jsonPath("$.data.records[0].supplierName").isNotEmpty());
    }

    @Test
    @DisplayName("供货商只看到自家商品，种子数据 3 个")
    void supplierOnlySeesOwn() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].supplierName").value("鲜丰食品有限公司"));
    }

    @Test
    @DisplayName("供货商看别人家的商品详情返回 403")
    void supplierCannotReadOthers() throws Exception {
        long potatoes = findProduct(adminToken(), "土豆")[0];

        mockMvc.perform(get("/api/products/" + potatoes)
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("采购员没有 product:create，新增被拒")
    void purchaserCannotCreate() throws Exception {
        String body = objectMapper.writeValueAsString(productBody("测试菜", null));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + tokenOf("buyer01", "buyer123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("供货商新增商品时请求里的 supplierId 被忽略，落在自家")
    void supplierCreateIgnoresSupplierId() throws Exception {
        long otherSupplierId = findProduct(adminToken(), "土豆")[1];
        String body = objectMapper.writeValueAsString(productBody("青椒", otherSupplierId));

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long newId = objectMapper.readTree(response).path("data").asLong();

        // 自己看得到
        mockMvc.perform(get("/api/products/" + newId)
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.supplierName").value("鲜丰食品有限公司"));

        // 另一家看不到
        mockMvc.perform(get("/api/products/" + newId)
                        .header("Authorization", "Bearer " + tokenOf("supplier02", "supplier123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("供货商改别人家的商品返回 403")
    void supplierCannotUpdateOthers() throws Exception {
        long[] potatoes = findProduct(adminToken(), "土豆");
        String body = objectMapper.writeValueAsString(productBody("土豆", potatoes[1]));

        mockMvc.perform(put("/api/products/" + potatoes[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("供货商能改自家商品价格")
    void supplierCanUpdateOwn() throws Exception {
        long[] pork = findProduct(adminToken(), "五花肉");
        Map<String, Object> body = productBody("五花肉", null);
        body.put("price", 35.00);

        mockMvc.perform(put("/api/products/" + pork[0])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + pork[0])
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(35.00));
    }

    @Test
    @DisplayName("同一供货商下重名商品返回 409")
    void duplicateNameInSameSupplier() throws Exception {
        String body = objectMapper.writeValueAsString(productBody("五花肉", null));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("管理员指定供货商给任意一家建商品")
    void adminCanCreateForAnySupplier() throws Exception {
        long grainSupplierId = findProduct(adminToken(), "花生油")[1];
        String body = objectMapper.writeValueAsString(productBody("面粉", grainSupplierId));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("单价为负返回 400")
    void negativePriceRejected() throws Exception {
        Map<String, Object> body = productBody("负数菜", null);
        body.put("price", -1);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", "Bearer " + supplier01Token()))
                .andExpect(status().isBadRequest());
    }
}
