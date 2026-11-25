package com.foodsupply.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RoleRequest {

    @NotBlank(message = "请输入角色编码")
    @Size(max = 50, message = "角色编码不能超过 50 个字符")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "角色编码只能是大写字母、数字和下划线")
    private String roleCode;

    @NotBlank(message = "请输入角色名称")
    @Size(max = 50, message = "角色名称不能超过 50 个字符")
    private String roleName;

    @Size(max = 255, message = "描述不能超过 255 个字符")
    private String description;
}
