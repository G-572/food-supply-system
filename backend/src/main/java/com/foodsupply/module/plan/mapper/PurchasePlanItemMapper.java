package com.foodsupply.module.plan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodsupply.module.plan.entity.PurchasePlanItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface PurchasePlanItemMapper extends BaseMapper<PurchasePlanItem> {

    /** 一次把多张计划的明细取出来，列表页按计划分组用 */
    @Select("""
            <script>
            SELECT * FROM purchase_plan_item
            WHERE plan_id IN
            <foreach collection="planIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY id
            </script>
            """)
    List<PurchasePlanItem> selectByPlanIds(@Param("planIds") List<Long> planIds);
}
