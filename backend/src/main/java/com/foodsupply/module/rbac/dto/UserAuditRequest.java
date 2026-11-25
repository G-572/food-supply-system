package com.foodsupply.module.rbac.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserAuditRequest {

    @NotNull(message = "请指定审核结果")
    private Boolean approved;

    /**
     * 审核通过时最终落在哪个角色。不传则沿用注册时申请的意向角色，
     * 传了也只接受 PURCHASER / SUPPLIER。
     */
    private String roleCode;

    /** 供货商账号审核通过时用来绑定供货商 */
    private Long supplierId;

    /** 驳回原因，驳回时建议填写 */
    private String reason;
}
