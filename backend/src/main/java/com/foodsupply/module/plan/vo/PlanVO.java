package com.foodsupply.module.plan.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class PlanVO {

    private Long id;

    private String planNo;

    private Long purchaserId;

    private String purchaserName;

    private LocalDate planDate;

    private String status;

    private BigDecimal totalAmount;

    private String remark;

    /** 列表页不带明细，详情才填 */
    private List<PlanItemVO> items;

    private LocalDateTime createdAt;
}
