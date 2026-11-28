package com.foodsupply.module.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodsupply.module.order.entity.PurchaseOrder;
import com.foodsupply.module.order.entity.PurchaseOrderItem;
import com.foodsupply.module.order.mapper.PurchaseOrderItemMapper;
import com.foodsupply.module.order.mapper.PurchaseOrderMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 发送拆单。核心是"一张计划按供货商拆成 N 张子单"，N 由明细里不同供货商家数决定。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlanSendTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PurchaseOrderMapper orderMapper;

    @Autowired
    private PurchaseOrderItemMapper orderItemMapper;

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

    private long productId(String keyword) throws Exception {
        String response = mockMvc.perform(get("/api/products")
                        .param("keyword", keyword)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").path("records").get(0).path("id").asLong();
    }

    /** 五花肉 10 斤 + 土豆 50 斤，跨两家供货商 */
    private long createCrossSupplierPlan() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "planDate", "2026-11-29",
                "items", List.of(
                        Map.of("productId", productId("五花肉"), "quantity", 10),
                        Map.of("productId", productId("土豆"), "quantity", 50))));

        String response = mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(response).path("data").asLong();
    }

    private List<PurchaseOrder> ordersOf(long planId) {
        return orderMapper.selectList(Wrappers.<PurchaseOrder>lambdaQuery()
                .eq(PurchaseOrder::getPlanId, planId)
                .orderByAsc(PurchaseOrder::getId));
    }

    @Test
    @DisplayName("跨两家供货商的计划拆成两张子单，金额各自独立")
    void splitIntoTwoOrders() throws Exception {
        long planId = createCrossSupplierPlan();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(485.0))
                .andExpect(jsonPath("$.data.orders[0].orderNo").value(
                        org.hamcrest.Matchers.startsWith("SO2026")))
                .andExpect(jsonPath("$.data.orders[0].supplierName").value("鲜丰食品有限公司"));

        List<PurchaseOrder> orders = ordersOf(planId);
        assertEquals(2, orders.size());
        assertEquals(0, new BigDecimal("325.00").compareTo(orders.get(0).getTotalAmount()));
        assertEquals(0, new BigDecimal("160.00").compareTo(orders.get(1).getTotalAmount()));
    }

    @Test
    @DisplayName("拆出来的子单初始状态都是待确认")
    void ordersStartPendingConfirm() throws Exception {
        long planId = createCrossSupplierPlan();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk());

        ordersOf(planId).forEach(o -> assertEquals("PENDING_CONFIRM", o.getStatus()));
    }

    @Test
    @DisplayName("明细快照进子单，名称和单价都对得上")
    void itemsAreSnapshotted() throws Exception {
        long planId = createCrossSupplierPlan();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk());

        Long firstOrderId = ordersOf(planId).get(0).getId();
        List<PurchaseOrderItem> items = orderItemMapper.selectList(
                Wrappers.<PurchaseOrderItem>lambdaQuery().eq(PurchaseOrderItem::getOrderId, firstOrderId));

        assertEquals(1, items.size());
        assertEquals("五花肉", items.get(0).getProductName());
        assertEquals("斤", items.get(0).getUnit());
        assertEquals(0, new BigDecimal("32.50").compareTo(items.get(0).getUnitPrice()));
        assertEquals(0, new BigDecimal("325.00").compareTo(items.get(0).getSubtotal()));
    }

    @Test
    @DisplayName("发送后计划状态变 SENT")
    void planBecomesSent() throws Exception {
        long planId = createCrossSupplierPlan();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plans/" + planId)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(jsonPath("$.data.status").value("SENT"));
    }

    @Test
    @DisplayName("同一张计划不能发两次")
    void cannotSendTwice() throws Exception {
        long planId = createCrossSupplierPlan();
        String token = buyerToken();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("发送后不能改也不能删")
    void sentPlanIsFrozen() throws Exception {
        long planId = createCrossSupplierPlan();
        String token = buyerToken();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(Map.of("productId", productId("大白菜"), "quantity", 1))));

        mockMvc.perform(put("/api/plans/" + planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/plans/" + planId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("发送后再改商品价格，已发出的子单单价不受影响")
    void snapshotStaysAfterPriceChange() throws Exception {
        long planId = createCrossSupplierPlan();
        String token = buyerToken();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 供货商把五花肉从 32.5 改成 99
        String supplierToken = tokenOf("supplier01", "supplier123");
        String update = objectMapper.writeValueAsString(Map.of(
                "name", "五花肉", "category", "肉类", "unit", "斤",
                "price", 99.00, "stockQuantity", 120));
        mockMvc.perform(put("/api/products/" + productId("五花肉"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update)
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk());

        Long firstOrderId = ordersOf(planId).get(0).getId();
        PurchaseOrderItem item = orderItemMapper.selectList(
                Wrappers.<PurchaseOrderItem>lambdaQuery()
                        .eq(PurchaseOrderItem::getOrderId, firstOrderId)).get(0);

        assertEquals(0, new BigDecimal("32.50").compareTo(item.getUnitPrice()));
    }

    @Test
    @DisplayName("只有一家供货商的计划只拆一张单")
    void singleSupplierOneOrder() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "items", List.of(
                        Map.of("productId", productId("五花肉"), "quantity", 2),
                        Map.of("productId", productId("猪排骨"), "quantity", 3))));

        String response = mockMvc.perform(post("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long planId = objectMapper.readTree(response).path("data").asLong();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1));
    }

    @Test
    @DisplayName("供货商没有 plan:send，发送被拒")
    void supplierCannotSend() throws Exception {
        long planId = createCrossSupplierPlan();

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + tokenOf("supplier01", "supplier123")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("发送前商品被下架则整单拒绝，不产生任何子单")
    void offSaleBlocksWholeSend() throws Exception {
        long planId = createCrossSupplierPlan();
        String supplierToken = tokenOf("supplier01", "supplier123");

        mockMvc.perform(patch("/api/products/" + productId("五花肉") + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SALE\"}")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/plans/" + planId + "/send")
                        .header("Authorization", "Bearer " + buyerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("下架")));

        assertEquals(0, ordersOf(planId).size());
    }
}
