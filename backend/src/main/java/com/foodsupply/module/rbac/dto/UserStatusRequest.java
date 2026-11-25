package com.foodsupply.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UserStatusRequest {

    /** 只允许在启用和停用之间切换；待审核与驳回走审核接口 */
    @NotBlank(message = "请指定目标状态")
    @Pattern(regexp = "^(ACTIVE|DISABLED)$", message = "状态只能是 ACTIVE 或 DISABLED")
    private String status;
}
