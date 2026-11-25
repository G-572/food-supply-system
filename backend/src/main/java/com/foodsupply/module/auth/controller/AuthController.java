package com.foodsupply.module.auth.controller;

import com.foodsupply.common.Result;
import com.foodsupply.module.auth.dto.LoginRequest;
import com.foodsupply.module.auth.dto.RegisterRequest;
import com.foodsupply.module.auth.service.AuthService;
import com.foodsupply.module.auth.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.ok("注册申请已提交，等待管理员审核", null);
    }

    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok("登录成功", authService.login(request));
    }
}
