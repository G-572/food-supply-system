package com.foodsupply.module.rbac.vo;

import lombok.Data;

@Data
public class PermissionVO {

    private Long id;

    /** 权限编码，如 order:confirm */
    private String permCode;

    private String permName;

    /** 所属模块，前端按它分组渲染 */
    private String module;
}
