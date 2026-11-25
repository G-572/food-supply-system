package com.foodsupply.module.rbac.controller;

import com.foodsupply.common.PageResult;
import com.foodsupply.common.Result;
import com.foodsupply.module.rbac.dto.UserCreateRequest;
import com.foodsupply.module.rbac.dto.UserStatusRequest;
import com.foodsupply.module.rbac.dto.UserUpdateRequest;
import com.foodsupply.module.rbac.service.UserService;
import com.foodsupply.module.rbac.vo.UserVO;
import com.foodsupply.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final CurrentUser currentUser;

    @GetMapping
    @PreAuthorize("hasAuthority('user:list')")
    public Result<PageResult<UserVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.ok(userService.page(pageNum, pageSize, keyword, status));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public Result<Long> create(@Valid @RequestBody UserCreateRequest request) {
        return Result.ok("账号已创建", userService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:update')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.update(id, request);
        return Result.ok("保存成功", null);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:update')")
    public Result<Void> changeStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        userService.changeStatus(id, request, currentUser.id());
        return Result.ok("状态已更新", null);
    }
}
