package com.foodsupply.module.product.controller;

import com.foodsupply.common.PageResult;
import com.foodsupply.common.Result;
import com.foodsupply.module.product.dto.ProductRequest;
import com.foodsupply.module.product.dto.ProductStatusRequest;
import com.foodsupply.module.product.service.ProductService;
import com.foodsupply.module.product.vo.ProductVO;
import com.foodsupply.security.CurrentUser;
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
 * 商品。读接口三类角色都有，写接口供货商和管理员都有权限码，
 * 但供货商只能落在自己名下，范围限制在 Service 里做。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('product:read')")
    public Result<PageResult<ProductVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return Result.ok(productService.page(pageNum, pageSize, keyword, supplierId,
                category, status, currentUser.supplierId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('product:read')")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.get(id, currentUser.supplierId()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('product:create')")
    public Result<Long> create(@Valid @RequestBody ProductRequest request) {
        return Result.ok("商品已新增", productService.create(request, currentUser.supplierId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        productService.update(id, request, currentUser.supplierId());
        return Result.ok("保存成功", null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('product:update')")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody ProductStatusRequest request) {
        productService.changeStatus(id, request, currentUser.supplierId());
        return Result.ok("状态已更新", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('product:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id, currentUser.supplierId());
        return Result.ok("已删除", null);
    }
}
