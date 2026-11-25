package com.foodsupply.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foodsupply.module.rbac.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 一次联表把用户的权限码全取出来。登录和过滤器都用它，
     * 避免先查角色再逐个查权限。
     */
    @Select("""
            SELECT DISTINCT p.perm_code
            FROM sys_user_role ur
            JOIN sys_role_permission rp ON rp.role_id = ur.role_id
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectPermissionCodes(@Param("userId") Long userId);

    /** 用户的角色编码，登录响应里要带上 */
    @Select("""
            SELECT DISTINCT r.role_code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodes(@Param("userId") Long userId);
}
