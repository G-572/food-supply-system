package com.foodsupply.module.rbac.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RoleVO {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    /** 该角色下的权限编码，前端用来回显勾选框 */
    private List<String> permissionCodes;

    /** 有多少个账号在用这个角色，删除前要检查 */
    private Long userCount;

    private LocalDateTime createdAt;
}
