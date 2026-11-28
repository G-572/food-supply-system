package com.foodsupply.module.plan;

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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanControllerTest {

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

    private String buyerToken() throws Exception {
        return tokenOf("buyer01", "buyer123");
    }

    /** 按名称找一个商品，返回 [id, supplierId] */
    private long[] findProduct(String keyword) throws Exception {
        String response = mockMvc.perform(get("/api/products")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        var first = objectMapper.readTree(response).path("data").path("records").get(0);
        return new long[]{first.path("id").asLong(), first.path("supplierId").asLong()};
    }

    private Map<String, Object> planBody(long productA, java.math.BigDecimal qtyA,
                                         long productB, java.math.BigDecimal qtyB) {
        return Map.of(
                "planDate", "2026-11-28",
                "remark", "食堂下周备货",
                "items", List.of(
                        Map.of("productId", productA, "quantity", qtyA),
                        Map.of("productId", productB, "quantity", qtyB)));
    }

    private long createPlan() throws Exception {
        long pork = findProduct("五花肉")[0];
        long potato = findProduct("土豆")[0];
        String body = objectMapper.writeValueAsString(
                planBody(pork, new java.math.BigDecimal("10"), potato, new java.math.BigDecimal("50")));

        String response = mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").asLong();
    }

    @Test
    @DisplayName("采购员建计划，跨两家供货商，合计金额按当前价格算")
    void createPlanAcrossSuppliers() throws Exception {
        long id = createPlan();

        mockMvc.perform(get("/api/plans/" + id)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.planNo").value(org.hamcrest.Matchers.startsWith("PP2026")))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                // 五花肉 32.50*10 + 土豆 3.20*50 = 325 + 160
                .andExpect(jsonPath("$.data.totalAmount").value(485.0))
                .andExpect(jsonPath("$.data.items[0].supplierName").isNotEmpty());
    }

    @Test
    @DisplayName("明细带上商品名称、单位和供货商")
    void itemCarriesProductInfo() throws Exception {
        long id = createPlan();

        mockMvc.perform(get("/api/plans/" + id)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].productName").value("五花肉"))
                .andExpect(jsonPath("$.data.items[0].unit").value("斤"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(32.5))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(325.0));
    }

    @Test
    @DisplayName("采购员列表只看到自己的计划")
    void buyerOnlySeesOwn() throws Exception {
        createPlan();

        mockMvc.perform(get("/api/plans").header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].purchaserName").value("张采购"))
                .andExpect(jsonPath("$.data.records[0].items").doesNotExist());
    }

    @Test
    @DisplayName("管理员能看采购员的计划")
    void adminSeesAll() throws Exception {
        createPlan();

        mockMvc.perform(get("/api/plans").header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("供货商没有 plan:read，看计划列表返回 403")
    void supplierForbidden() throws Exception {
        mockMvc.perform(get("/api/plans")
                        .header("Authorization", "Bearer " + tokenOf("supplier01", "supplier123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("未登录返回 401")
    void anonymousUnauthorized() throws Exception {
        mockMvc.perform(get("/api/plans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("同一商品重复出现时拒绝")
    void duplicateProductRejected() throws Exception {
        long pork = findProduct("五花肉")[0];
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(
                        Map.of("productId", pork, "quantity", 5),
                        Map.of("productId", pork, "quantity", 8))));

        mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("引用已下架商品时拒绝")
    void offSaleProductRejected() throws Exception {
        long pork = findProduct("五花肉")[0];
        String supplierToken = tokenOf("supplier01", "supplier123");

        mockMvc.perform(patch("/api/products/" + pork + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SALE\"}")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", pork, "quantity", 5))));

        mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("下架")));
    }

    @Test
    @DisplayName("修改草稿会全量替换明细并重算金额")
    void updateReplacesItems() throws Exception {
        long id = createPlan();
        long cabbage = findProduct("大白菜")[0];
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", cabbage, "quantity", 100))));

        mockMvc.perform(put("/api/plans/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plans/" + id)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("大白菜"))
                // 2.10 * 100
                .andExpect(jsonPath("$.data.totalAmount").value(210.0));
    }

    @Test
    @DisplayName("草稿可以删除")
    void deleteDraft() throws Exception {
        long id = createPlan();

        mockMvc.perform(delete("/api/plans/" + id)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plans/" + id)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isNotFound());
    }
}
