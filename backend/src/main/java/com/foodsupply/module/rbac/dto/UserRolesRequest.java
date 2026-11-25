package com.foodsupply.module.rbac.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class UserRolesRequest {

    /** 全量覆盖，不是增量追加 */
    @NotEmpty(message = "请至少选择一个角色")
    private List<String> roleCodes;
}
