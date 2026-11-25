package com.foodsupply.module.auth.controller;

import com.foodsupply.common.Result;
import com.foodsupply.module.auth.dto.LoginRequest;
import com.foodsupply.module.auth.dto.RegisterRequest;
import com.foodsupply.module.auth.service.AuthService;
import com.foodsupply.module.auth.vo.LoginVO;
import com.foodsupply.module.auth.vo.UserInfoVO;
import com.foodsupply.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private final CurrentUser currentUser;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.ok("注册申请已提交，等待管理员审核", null);
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(currentUser.id());
        return Result.ok("已退出登录", null);
    }

    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.me(currentUser.id()));
    }
}
