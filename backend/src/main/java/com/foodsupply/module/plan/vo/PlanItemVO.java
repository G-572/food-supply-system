package com.foodsupply.module.plan.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 明细展示。名称、单价、供货商都是查商品表补上的，
 * 发送之后这些值会被快照进子单明细，那时以子单为准。
 */
@Data
public class PlanItemVO {

    private Long id;

    private Long productId;

    private String productName;

    private String unit;

    private BigDecimal unitPrice;

    private BigDecimal quantity;

    private BigDecimal subtotal;

    private Long supplierId;

    private String supplierName;
}
