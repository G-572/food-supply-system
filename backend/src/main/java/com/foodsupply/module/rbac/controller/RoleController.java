package com.foodsupply.module.rbac.controller;

import com.foodsupply.common.Result;
import com.foodsupply.module.rbac.dto.RolePermissionsRequest;
import com.foodsupply.module.rbac.dto.RoleRequest;
import com.foodsupply.module.rbac.service.RoleService;
import com.foodsupply.module.rbac.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('role:list')")
    public Result<List<RoleVO>> list() {
        return Result.ok(roleService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:create')")
    public Result<Long> create(@Valid @RequestBody RoleRequest request) {
        return Result.ok("角色已创建", roleService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody RoleRequest request) {
        roleService.update(id, request);
        return Result.ok("保存成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return Result.ok("角色已删除", null);
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('role:list')")
    public Result<List<String>> permissions(@PathVariable Long id) {
        return Result.ok(roleService.getPermissionCodes(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('role:assign-permission')")
    public Result<Void> assignPermissions(@PathVariable Long id,
                                          @Valid @RequestBody RolePermissionsRequest request) {
        roleService.assignPermissions(id, request);
        return Result.ok("权限已更新", null);
    }
}
