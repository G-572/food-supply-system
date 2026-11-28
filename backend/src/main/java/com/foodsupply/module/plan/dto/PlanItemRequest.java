package com.foodsupply.module.plan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlanItemRequest {

    @NotNull(message = "请选择商品")
    private Long productId;

    @NotNull(message = "请输入数量")
    @DecimalMin(value = "0.01", message = "数量必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "数量最多两位小数")
    private BigDecimal quantity;
}
