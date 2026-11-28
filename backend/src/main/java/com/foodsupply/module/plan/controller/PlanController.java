package com.foodsupply.module.plan.controller;

import com.foodsupply.common.PageResult;
import com.foodsupply.common.Result;
import com.foodsupply.module.plan.dto.PlanRequest;
import com.foodsupply.module.plan.service.PlanService;
import com.foodsupply.module.plan.vo.PlanVO;
import com.foodsupply.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 采购计划。采购员和管理员都有 plan:* 权限码，
 * 差别在数据范围：采购员只看自己的，管理员看全部。
 */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    /** 管理员专属权限，用它区分身份，采购员没有 user:list */
    private static final String ADMIN_MARK = "user:list";

    private final PlanService planService;

    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('plan:read')")
    public Result<PageResult<PlanVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate planDate,
            @RequestParam(required = false) Long purchaserId) {
        return Result.ok(planService.page(pageNum, pageSize, status, planDate,
                purchaserId, currentUser.id(), currentUser.hasAuthority(ADMIN_MARK)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:read')")
    public Result<PlanVO> detail(@PathVariable Long id) {
        return Result.ok(planService.get(id, currentUser.id(), currentUser.hasAuthority(ADMIN_MARK)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('plan:create')")
    public Result<Long> create(@Valid @RequestBody PlanRequest request) {
        return Result.ok("计划已创建", planService.create(request, currentUser.id()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PlanRequest request) {
        planService.update(id, request, currentUser.id(), currentUser.hasAuthority(ADMIN_MARK));
        return Result.ok("保存成功", null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('plan:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        planService.delete(id, currentUser.id(), currentUser.hasAuthority(ADMIN_MARK));
        return Result.ok("已删除", null);
    }
}
