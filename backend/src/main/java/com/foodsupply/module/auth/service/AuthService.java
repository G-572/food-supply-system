package com.foodsupply.module.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foodsupply.common.BizException;
import com.foodsupply.module.auth.dto.LoginRequest;
import com.foodsupply.module.auth.dto.RegisterRequest;
import com.foodsupply.module.auth.vo.LoginVO;
import com.foodsupply.module.auth.vo.UserInfoVO;
import com.foodsupply.module.rbac.entity.SysRole;
import com.foodsupply.module.rbac.entity.SysUser;
import com.foodsupply.module.rbac.entity.SysUserRole;
import com.foodsupply.module.rbac.mapper.SysRoleMapper;
import com.foodsupply.module.rbac.mapper.SysUserMapper;
import com.foodsupply.module.rbac.mapper.SysUserRoleMapper;
import com.foodsupply.security.JwtProperties;
import com.foodsupply.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_REJECTED = "REJECTED";

    /** 新注册用户统一先落最低权限角色，审核通过后再按申请的意向角色调整 */
    private static final String DEFAULT_ROLE_CODE = "SUPPLIER";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final JwtProperties jwtProperties;

    @Transactional
    public void register(RegisterRequest request) {
        Long exists = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("用户名已被占用");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setStatus(STATUS_PENDING);
        user.setAppliedRole(request.getAppliedRole());
        user.setTokenVersion(0);
        userMapper.insert(user);

        SysRole defaultRole = roleMapper.selectOne(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, DEFAULT_ROLE_CODE));
        if (defaultRole == null) {
            throw new IllegalStateException("默认角色 " + DEFAULT_ROLE_CODE + " 不存在，请检查种子数据");
        }
        SysUserRole userRole = new SysUserRole();
        userRole.setUserId(user.getId());
        userRole.setRoleId(defaultRole.getId());
        userRoleMapper.insert(userRole);

        log.info("新用户注册: username={}, 申请身份={}", user.getUsername(), request.getAppliedRole());
    }

    public LoginVO login(LoginRequest request) {
        SysUser user = userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.getUsername()));

        // 用户不存在和密码错误给同一个提示，避免被拿来枚举用户名
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw BizException.forbidden(statusHint(user.getStatus()));
        }

        String token = tokenProvider.generate(user.getId(), user.getUsername(), user.getTokenVersion());
        return LoginVO.builder()
                .token(token)
                .expiresIn(jwtProperties.getExpireMinutes() * 60)
                .user(buildUserInfo(user))
                .build();
    }

    /**
     * 退出登录：token_version + 1，该用户此前签发的所有 Token 立刻失效。
     * 没有 Redis，黑名单做不了，用版本号达到同样的效果。
     */
    @Transactional
    public void logout(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, userId)
                .set(SysUser::getTokenVersion, user.getTokenVersion() + 1));
        log.info("用户退出登录: userId={}", userId);
    }

    public UserInfoVO me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return buildUserInfo(user);
    }

    private String statusHint(String status) {
        return switch (status) {
            case STATUS_PENDING -> "账号正在等待管理员审核，暂时无法登录";
            case STATUS_REJECTED -> "注册申请已被驳回，请联系管理员";
            default -> "账号已被停用，请联系管理员";
        };
    }

    private UserInfoVO buildUserInfo(SysUser user) {
        List<String> permissions = userMapper.selectPermissionCodes(user.getId());
        List<String> roles = userMapper.selectRoleCodes(user.getId());
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .status(user.getStatus())
                .supplierId(user.getSupplierId())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
