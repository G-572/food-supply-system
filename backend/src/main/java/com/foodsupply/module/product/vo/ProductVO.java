package com.foodsupply.module.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProductVO {

    private Long id;

    private Long supplierId;

    /** 列表要显示"哪家供的"，逐条查会 N+1，服务里一次性批量取 */
    private String supplierName;

    private String name;

    private String category;

    private String unit;

    private BigDecimal price;

    private BigDecimal stockQuantity;

    private String status;

    private String remark;

    private LocalDateTime updatedAt;
}
