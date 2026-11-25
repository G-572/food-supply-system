package com.foodsupply.module.rbac.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foodsupply.module.rbac.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SysUserMapperTest {

    @Autowired
    private SysUserMapper userMapper;

    private SysUser findByUsername(String username) {
        return userMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
    }

    @Test
    @DisplayName("admin 挂 ADMIN 角色，权限码 34 条")
    void adminHasAllPermissions() {
        SysUser admin = findByUsername("admin");
        assertNotNull(admin);

        List<String> perms = userMapper.selectPermissionCodes(admin.getId());
        assertEquals(34, perms.size());
        assertTrue(perms.contains("user:audit"));
        assertTrue(perms.contains("settlement:view"));
    }

    @Test
    @DisplayName("采购员拿不到 user:* 权限")
    void purchaserHasNoUserPermission() {
        SysUser buyer = findByUsername("buyer01");
        assertNotNull(buyer);

        List<String> perms = userMapper.selectPermissionCodes(buyer.getId());
        assertEquals(13, perms.size());
        assertFalse(perms.stream().anyMatch(p -> p.startsWith("user:")));
        assertTrue(perms.contains("plan:send"));
    }

    @Test
    @DisplayName("角色编码能正确取出")
    void roleCodesLoaded() {
        SysUser supplier = findByUsername("supplier01");
        assertNotNull(supplier);

        assertEquals(List.of("SUPPLIER"), userMapper.selectRoleCodes(supplier.getId()));
        assertEquals(1L, supplier.getSupplierId());
    }
}
