package com.foodsupply.module.rbac.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foodsupply.common.BizException;
import com.foodsupply.module.rbac.dto.RolePermissionsRequest;
import com.foodsupply.module.rbac.dto.RoleRequest;
import com.foodsupply.module.rbac.entity.SysPermission;
import com.foodsupply.module.rbac.entity.SysRole;
import com.foodsupply.module.rbac.entity.SysRolePermission;
import com.foodsupply.module.rbac.entity.SysUserRole;
import com.foodsupply.module.rbac.mapper.SysPermissionMapper;
import com.foodsupply.module.rbac.mapper.SysRoleMapper;
import com.foodsupply.module.rbac.mapper.SysRolePermissionMapper;
import com.foodsupply.module.rbac.mapper.SysUserRoleMapper;
import com.foodsupply.module.rbac.vo.PermissionVO;
import com.foodsupply.module.rbac.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String ROLE_ADMIN = "ADMIN";

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;

    public List<RoleVO> list() {
        List<SysRole> roles = roleMapper.selectList(
                Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
        return roles.stream().map(this::toVO).toList();
    }

    public List<PermissionVO> listPermissions() {
        return permissionMapper.selectList(
                        Wrappers.<SysPermission>lambdaQuery()
                                .orderByAsc(SysPermission::getModule)
                                .orderByAsc(SysPermission::getPermCode))
                .stream()
                .map(p -> {
                    PermissionVO vo = new PermissionVO();
                    vo.setId(p.getId());
                    vo.setPermCode(p.getPermCode());
                    vo.setPermName(p.getPermName());
                    vo.setModule(p.getModule());
                    return vo;
                })
                .toList();
    }

    @Transactional
    public Long create(RoleRequest request) {
        Long exists = roleMapper.selectCount(
                Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, request.getRoleCode()));
        if (exists != null && exists > 0) {
            throw BizException.conflict("角色编码已存在");
        }
        SysRole role = new SysRole();
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        roleMapper.insert(role);
        log.info("新增角色: {}", role.getRoleCode());
        return role.getId();
    }

    @Transactional
    public void update(Long id, RoleRequest request) {
        SysRole role = requireRole(id);
        if (ROLE_ADMIN.equals(role.getRoleCode())
                && !ROLE_ADMIN.equals(request.getRoleCode())) {
            throw BizException.badRequest("内置管理员的角色编码不可修改");
        }
        Long exists = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, request.getRoleCode())
                .ne(SysRole::getId, id));
        if (exists != null && exists > 0) {
            throw BizException.conflict("角色编码已存在");
        }
        role.setRoleCode(request.getRoleCode());
        role.setRoleName(request.getRoleName());
        role.setDescription(request.getDescription());
        roleMapper.updateById(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = requireRole(id);
        if (ROLE_ADMIN.equals(role.getRoleCode())) {
            throw BizException.badRequest("内置管理员角色不可删除");
        }
        Long inUse = userRoleMapper.selectCount(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
        if (inUse != null && inUse > 0) {
            throw BizException.conflict("该角色下还有 " + inUse + " 个账号，无法删除");
        }
        // 角色权限关联先清掉，否则外键约束会挡
        rolePermissionMapper.delete(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        log.info("删除角色: {}", role.getRoleCode());
    }

    public List<String> getPermissionCodes(Long roleId) {
        requireRole(roleId);
        return rolePermissionMapper.selectList(
                        Wrappers.<SysRolePermission>lambdaQuery()
                                .eq(SysRolePermission::getRoleId, roleId))
                .stream()
                .map(rp -> permissionMapper.selectById(rp.getPermissionId()))
                .filter(p -> p != null)
                .map(SysPermission::getPermCode)
                .toList();
    }

    /** 全量覆盖权限。改完立即生效——过滤器每次请求都实时查库 */
    @Transactional
    public void assignPermissions(Long roleId, RolePermissionsRequest request) {
        SysRole role = requireRole(roleId);
        if (ROLE_ADMIN.equals(role.getRoleCode()) && request.getPermissionIds().isEmpty()) {
            throw BizException.badRequest("管理员角色不能清空权限");
        }

        List<Long> ids = request.getPermissionIds().stream().distinct().toList();
        if (!ids.isEmpty()) {
            Long valid = permissionMapper.selectCount(
                    Wrappers.<SysPermission>lambdaQuery().in(SysPermission::getId, ids));
            if (valid == null || valid != ids.size()) {
                throw BizException.badRequest("存在无效的权限 ID");
            }
        }

        rolePermissionMapper.delete(
                Wrappers.<SysRolePermission>lambdaQuery().eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : ids) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(permissionId);
            rolePermissionMapper.insert(rp);
        }
        log.info("角色权限已更新: role={}, 权限数={}", role.getRoleCode(), ids.size());
    }

    private SysRole requireRole(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw BizException.notFound("角色不存在");
        }
        return role;
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDescription(role.getDescription());
        vo.setCreatedAt(role.getCreatedAt());
        vo.setPermissionCodes(getPermissionCodes(role.getId()));
        vo.setUserCount(userRoleMapper.selectCount(
                Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, role.getId())));
        return vo;
    }
}
