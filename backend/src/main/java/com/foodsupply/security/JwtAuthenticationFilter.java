package com.foodsupply.security;

import com.foodsupply.module.rbac.entity.SysUser;
import com.foodsupply.module.rbac.mapper.SysUserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Authorization 头，校验通过就把认证信息写进 SecurityContext。
 * 校验顺序：签名与有效期 -> 用户是否存在 -> 账号是否 ACTIVE -> token_version 是否一致。
 * 任何一步不通过都只是不设置认证，交给后续的 authenticated() 返回 401。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final SysUserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            authenticate(header.substring(PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            Claims claims = tokenProvider.parse(token);
            Long userId = tokenProvider.getUserId(claims);
            int versionInToken = tokenProvider.getTokenVersion(claims);

            SysUser user = userMapper.selectById(userId);
            if (user == null || !"ACTIVE".equals(user.getStatus())) {
                return;
            }
            // 退出登录时 token_version +1，这里对不上就说明这个 Token 已经作废
            if (user.getTokenVersion() != null && user.getTokenVersion() != versionInToken) {
                return;
            }

            List<SimpleGrantedAuthority> authorities = userMapper.selectPermissionCodes(userId).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            LoginUser principal = new LoginUser(user.getId(), user.getUsername(), user.getSupplierId());
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
        }
    }
}
