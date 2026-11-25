package com.foodsupply.module.rbac.controller;

import com.foodsupply.common.Result;
import com.foodsupply.module.rbac.service.RoleService;
import com.foodsupply.module.rbac.vo.PermissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final RoleService roleService;

    /** 权限字典，前端渲染角色授权页的勾选框用 */
    @GetMapping
    @PreAuthorize("hasAuthority('permission:list')")
    public Result<List<PermissionVO>> list() {
        return Result.ok(roleService.listPermissions());
    }
}
