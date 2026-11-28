package com.foodsupply.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodsupply.module.product.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ProductMapper extends BaseMapper<Product> {

    /** 已经被历史订单引用过的商品不能删，明细表有外键，删了会直接报错 */
    @Select("SELECT COUNT(*) FROM purchase_order_item WHERE product_id = #{productId}")
    long countOrderItems(@Param("productId") Long productId);
}
