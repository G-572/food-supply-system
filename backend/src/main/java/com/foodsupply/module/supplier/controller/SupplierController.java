package com.foodsupply.module.supplier.controller;

import com.foodsupply.common.PageResult;
import com.foodsupply.common.Result;
import com.foodsupply.module.supplier.dto.SupplierRequest;
import com.foodsupply.module.supplier.dto.SupplierStatusRequest;
import com.foodsupply.module.supplier.service.SupplierService;
import com.foodsupply.module.supplier.vo.SupplierVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供货商。查看权限采购员和供货商都有，增删改只有管理员。
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAuthority('supplier:read')")
    public Result<PageResult<SupplierVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(supplierService.page(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:read')")
    public Result<SupplierVO> detail(@PathVariable Long id) {
        return Result.ok(supplierService.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('supplier:create')")
    public Result<Long> create(@Valid @RequestBody SupplierRequest request) {
        return Result.ok("供货商已新增", supplierService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        supplierService.update(id, request);
        return Result.ok("保存成功", null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('supplier:update')")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody SupplierStatusRequest request) {
        supplierService.changeStatus(id, request);
        return Result.ok("状态已更新", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('supplier:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok("已删除", null);
    }
}
