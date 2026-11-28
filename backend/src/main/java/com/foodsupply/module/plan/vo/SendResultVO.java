package com.foodsupply.module.plan.vo;

import com.foodsupply.module.order.vo.OrderBriefVO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发送计划的结果。前端拿它弹"已拆成 N 张子单"。
 */
@Data
public class SendResultVO {

    private Long planId;

    private String planNo;

    private int orderCount;

    private BigDecimal totalAmount;

    private List<OrderBriefVO> orders;
}
