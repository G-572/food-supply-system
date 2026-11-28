package com.foodsupply.module.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 拆单结果里的一张子单摘要，只够前端展示"拆成了哪几单"。
 */
@Data
public class OrderBriefVO {

    private Long id;

    private String orderNo;

    private Long supplierId;

    private String supplierName;

    private BigDecimal totalAmount;

    private int itemCount;
}
