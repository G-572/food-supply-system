package com.foodsupply.security;

import com.foodsupply.common.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 取当前登录用户。Controller 里直接注入，不用到处翻 SecurityContext。
 */
@Component
public class CurrentUser {

    public LoginUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser user)) {
            throw BizException.unauthorized("未登录或登录已过期");
        }
        return user;
    }

    public Long id() {
        return get().id();
    }

    /** 供货商账号用它限定数据范围，管理员和采购员为 null */
    public Long supplierId() {
        return get().supplierId();
    }

    /**
     * 当前用户有没有某个权限码。用在"管理员和采购员权限码重合、
     * 但数据范围不同"的地方，比如计划列表管理员能看全部。
     */
    public boolean hasAuthority(String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> code.equals(a.getAuthority()));
    }
}
