package com.foodsupply.module.rbac.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RolePermissionsRequest {

    /** 全量覆盖，传空列表表示清空该角色的权限 */
    @NotNull(message = "权限列表不能为空，清空请传空数组")
    private List<Long> permissionIds;
}
