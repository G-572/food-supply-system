package com.foodsupply.module.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProductStatusRequest {

    @NotBlank(message = "请选择商品状态")
    @Pattern(regexp = "ON_SALE|OFF_SALE", message = "状态只能是 ON_SALE 或 OFF_SALE")
    private String status;
}
