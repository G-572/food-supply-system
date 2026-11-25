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
}
