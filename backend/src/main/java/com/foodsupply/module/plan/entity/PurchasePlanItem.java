package com.foodsupply.module.plan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计划明细。只记商品和数量，名称、单价、供货商这些发送时按商品表重新取，
 * 免得草稿放到第二天价格变了还对不上。
 */
@Data
@TableName("purchase_plan_item")
public class PurchasePlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long productId;

    private BigDecimal quantity;

    private LocalDateTime createdAt;
}
