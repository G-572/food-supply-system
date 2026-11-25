package com.foodsupply.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foodsupply.common.BizException;
import com.foodsupply.common.ResultCode;
import com.foodsupply.module.auth.dto.LoginRequest;
import com.foodsupply.module.auth.dto.RegisterRequest;
import com.foodsupply.module.auth.vo.LoginVO;
import com.foodsupply.module.rbac.entity.SysUser;
import com.foodsupply.module.rbac.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private RegisterRequest registerRequest(String username, String appliedRole) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("abc123");
        request.setRealName("测试用户");
        request.setPhone("13900000099");
        request.setAppliedRole(appliedRole);
        return request;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    @Test
    @DisplayName("注册后落 PENDING + SUPPLIER，密码以 BCrypt 存储")
    void registerCreatesPendingUser() {
        authService.register(registerRequest("newbuyer", "PURCHASER"));

        SysUser saved = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, "newbuyer"));

        assertNotNull(saved);
        assertEquals("PENDING", saved.getStatus());
        assertEquals("PURCHASER", saved.getAppliedRole());
        assertNotEquals("abc123", saved.getPassword());
        assertTrue(passwordEncoder.matches("abc123", saved.getPassword()));
        assertEquals(List.of("SUPPLIER"), userMapper.selectRoleCodes(saved.getId()));
    }

    @Test
    @DisplayName("用户名已存在时返回 409")
    void registerDuplicateUsername() {
        BizException e = assertThrows(BizException.class,
                () -> authService.register(registerRequest("admin", "PURCHASER")));

        assertEquals(ResultCode.CONFLICT, e.getResultCode());
    }

    @Test
    @DisplayName("登录成功返回 Token、角色与权限")
    void loginSuccess() {
        LoginVO vo = authService.login(loginRequest("buyer01", "buyer123"));

        assertNotNull(vo.getToken());
        assertTrue(vo.getExpiresIn() > 0);
        assertEquals("buyer01", vo.getUser().getUsername());
        assertEquals(List.of("PURCHASER"), vo.getUser().getRoles());
        assertTrue(vo.getUser().getPermissions().contains("plan:send"));
        assertFalse(vo.getUser().getPermissions().contains("user:list"));
    }

    @Test
    @DisplayName("密码错误与用户不存在返回同一提示")
    void loginWrongPassword() {
        BizException wrongPwd = assertThrows(BizException.class,
                () -> authService.login(loginRequest("buyer01", "not-my-password")));
        BizException noUser = assertThrows(BizException.class,
                () -> authService.login(loginRequest("nobody_here", "abc123")));

        assertEquals(ResultCode.UNAUTHORIZED, wrongPwd.getResultCode());
        assertEquals(ResultCode.UNAUTHORIZED, noUser.getResultCode());
        assertEquals(wrongPwd.getMessage(), noUser.getMessage());
    }

    @Test
    @DisplayName("待审核账号不能登录")
    void pendingUserCannotLogin() {
        authService.register(registerRequest("waiting01", "SUPPLIER"));

        BizException e = assertThrows(BizException.class,
                () -> authService.login(loginRequest("waiting01", "abc123")));

        assertEquals(ResultCode.FORBIDDEN, e.getResultCode());
        assertTrue(e.getMessage().contains("审核"));
    }
}
