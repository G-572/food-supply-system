package com.foodsupply.module.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 子单。采购计划发送时按供货商拆出来，一单只对一家供货商。
 */
@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** SO + 日期 + 三位流水 */
    private String orderNo;

    private Long planId;

    /** 由明细商品的归属反查出来的，不接受前端传值 */
    private Long supplierId;

    /** PENDING_CONFIRM / CONFIRMED / DELIVERED / RECEIVED / REJECTED / NOT_DELIVERED */
    private String status;

    private BigDecimal totalAmount;

    private String notDeliveredReason;

    private LocalDateTime confirmedAt;

    private LocalDateTime deliveredAt;

    /** 月结按这个时间归月 */
    private LocalDateTime receivedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
