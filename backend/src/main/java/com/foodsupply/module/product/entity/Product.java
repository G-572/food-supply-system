package com.foodsupply.module.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品。一个商品只属于一家供货商，采购计划拆单就是按这里的 supplierId 分组的。
 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属供货商，供货商账号只能动自己这一份 */
    private Long supplierId;

    private String name;

    /** 蔬菜 / 肉类 / 粮油 ...，暂时不做字典表 */
    private String category;

    private String unit;

    /** 单价，由供货商维护 */
    private BigDecimal price;

    private BigDecimal stockQuantity;

    /** ON_SALE / OFF_SALE */
    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
