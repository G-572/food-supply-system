package com.foodsupply.module.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PlanRequest {

    /** 不传就按今天算，前端选日期时才传 */
    private LocalDate planDate;

    @Size(max = 255, message = "备注不能超过 255 个字")
    private String remark;

    @NotEmpty(message = "请至少添加一个商品")
    @Valid
    private List<PlanItemRequest> items;
}
