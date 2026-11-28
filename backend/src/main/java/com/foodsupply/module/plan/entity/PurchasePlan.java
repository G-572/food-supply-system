package com.foodsupply.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购计划。一天可以建多张，一张可以跨供货商，发送时才拆单。
 */
@Data
@TableName("purchase_plan")
public class PurchasePlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** PP + 日期 + 三位流水，唯一 */
    private String planNo;

    private Long purchaserId;

    private LocalDate planDate;

    /** DRAFT / SENT，只有 DRAFT 能改 */
    private String status;

    private BigDecimal totalAmount;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
