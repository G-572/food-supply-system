package com.foodsupply.module.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductRequest {

    /**
     * 只对管理员有意义。供货商账号调接口时传什么都忽略，
     * 一律按登录账号绑定的供货商落库，省得有人试着改别人的商品。
     */
    private Long supplierId;

    @NotBlank(message = "请输入商品名称")
    @Size(max = 100, message = "名称不能超过 100 个字")
    private String name;

    @Size(max = 50, message = "分类不能超过 50 个字")
    private String category;

    @NotBlank(message = "请输入计量单位")
    @Size(max = 20, message = "单位不能超过 20 个字")
    private String unit;

    @NotNull(message = "请输入单价")
    @DecimalMin(value = "0.00", message = "单价不能为负数")
    @Digits(integer = 10, fraction = 2, message = "单价最多两位小数")
    private BigDecimal price;

    @NotNull(message = "请输入库存数量")
    @DecimalMin(value = "0.00", message = "库存不能为负数")
    @Digits(integer = 10, fraction = 2, message = "库存最多两位小数")
    private BigDecimal stockQuantity;

    @Size(max = 255, message = "备注不能超过 255 个字")
    private String remark;
}
