package com.foodsupply.security;

/**
 * 放进 SecurityContext 的当前用户，只带业务需要的最小信息。
 * 权限码不重复存，用的时候从 authorities 里取。
 */
public record LoginUser(Long id, String username, Long supplierId) {
}
