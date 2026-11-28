package com.foodsupply.module.supplier.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodsupply.module.supplier.entity.Supplier;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SupplierMapper extends BaseMapper<Supplier> {

    /**
     * 删除前查有没有商品挂在这个供货商下面。
     * 走原生 SQL 是为了不引入 product 模块的实体，两边解耦。
     */
    @Select("SELECT COUNT(*) FROM product WHERE supplier_id = #{supplierId}")
    long countProducts(@Param("supplierId") Long supplierId);

    /** 供货商账号绑定关系，删之前也得看一眼 */
    @Select("SELECT COUNT(*) FROM sys_user WHERE supplier_id = #{supplierId}")
    long countUsers(@Param("supplierId") Long supplierId);
}
