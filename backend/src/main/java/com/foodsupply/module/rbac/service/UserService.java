package com.foodsupply.module.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foodsupply.common.BizException;
import com.foodsupply.common.PageResult;
import com.foodsupply.module.rbac.dto.UserCreateRequest;
import com.foodsupply.module.rbac.dto.UserStatusRequest;
import com.foodsupply.module.rbac.dto.UserUpdateRequest;
import com.foodsupply.module.rbac.entity.SysRole;
import com.foodsupply.module.rbac.entity.SysUser;
import com.foodsupply.module.rbac.entity.SysUserRole;
import com.foodsupply.module.rbac.mapper.SysRoleMapper;
import com.foodsupply.module.rbac.mapper.SysUserMapper;
import com.foodsupply.module.rbac.mapper.SysUserRoleMapper;
import com.foodsupply.module.rbac.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_SUPPLIER = "SUPPLIER";

    /** 接口只能建这两种角色的账号，管理员由种子数据预置，保证全系统唯一的 ADMIN */
    private static final Set<String> CREATABLE_ROLES = Set.of("PURCHASER", "SUPPLIER");

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户列表。keyword 同时匹配登录名和姓名，status 可选。
     * 角色先按单条查，等数据量上来再改成一次批量取（TODO）。
     */
    public PageResult<UserVO> page(int pageNum, int pageSize, String keyword, String status) {
        int current = Math.max(pageNum, 1);
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysUser> wrapper = Wrappers.<SysUser>lambdaQuery()
                .eq(StringUtils.hasText(status), SysUser::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or()
                        .like(SysUser::getRealName, keyword))
                .orderByDesc(SysUser::getId);

        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page, this::toVO);
    }

    @Transactional
    public Long create(UserCreateRequest request) {
        Long exists = userMapper.selectCount(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("用户名已被占用");
        }

        List<SysRole> roles = resolveRoles(request.getRoleCodes());
        if (roles.stream().anyMatch(r -> ROLE_ADMIN.equals(r.getRoleCode()))) {
            throw BizException.badRequest("管理员账号不通过接口创建");
        }
        boolean isSupplier = roles.stream().anyMatch(r -> ROLE_SUPPLIER.equals(r.getRoleCode()));
        if (isSupplier && request.getSupplierId() == null) {
            throw BizException.badRequest("供货商账号必须绑定一家供货商");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setStatus(STATUS_ACTIVE);
        user.setSupplierId(isSupplier ? request.getSupplierId() : null);
        user.setTokenVersion(0);
        userMapper.insert(user);

        for (SysRole role : roles) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(user.getId());
            userRole.setRoleId(role.getId());
            userRoleMapper.insert(userRole);
        }

        log.info("管理员创建账号: username={}, roles={}", user.getUsername(), request.getRoleCodes());
        return user.getId();
    }

    @Transactional
    public void update(Long id, UserUpdateRequest request) {
        SysUser user = requireUser(id);
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setSupplierId(request.getSupplierId());
        userMapper.updateById(user);
    }

    @Transactional
    public void changeStatus(Long id, UserStatusRequest request, Long operatorId) {
        SysUser user = requireUser(id);
        if (id.equals(operatorId) && STATUS_DISABLED.equals(request.getStatus())) {
            throw BizException.badRequest("不能停用自己的账号");
        }
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, id)
                .set(SysUser::getStatus, request.getStatus()));
        log.info("用户状态变更: id={}, status={}", id, request.getStatus());
    }

    private SysUser requireUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return user;
    }

    private List<SysRole> resolveRoles(List<String> roleCodes) {
        List<String> codes = roleCodes.stream().distinct().toList();
        for (String code : codes) {
            if (!CREATABLE_ROLES.contains(code)) {
                throw BizException.badRequest("不支持的角色: " + code);
            }
        }
        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery().in(SysRole::getRoleCode, codes));
        if (roles.size() != codes.size()) {
            throw BizException.badRequest("存在无效的角色编码");
        }
        return roles;
    }

    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setStatus(user.getStatus());
        vo.setAppliedRole(user.getAppliedRole());
        vo.setSupplierId(user.getSupplierId());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setRoles(userMapper.selectRoleCodes(user.getId()));
        return vo;
    }
}
