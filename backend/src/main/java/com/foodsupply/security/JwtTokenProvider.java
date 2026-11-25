package com.foodsupply.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Token 的签发与解析。
 * claims 里带 userId(sub)、username、ver(token_version)，解析后由过滤器逐项校验。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_USERNAME = "uname";
    private static final String CLAIM_VERSION = "ver";

    private final JwtProperties properties;
    private SecretKey key;

    @PostConstruct
    void init() {
        String secret = properties.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("app.jwt.secret 未配置或长度不足 32 字节");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param tokenVersion 签发时用户的 token_version，退出登录后该值 +1，旧 Token 随即失效
     */
    public String generate(Long userId, String username, int tokenVersion) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getExpireMinutes() * 60);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签，失败（过期、签名不对、格式错）直接抛 JwtException，由调用方转成 401。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String getUsername(Claims claims) {
        return claims.get(CLAIM_USERNAME, String.class);
    }

    public int getTokenVersion(Claims claims) {
        Integer ver = claims.get(CLAIM_VERSION, Integer.class);
        return ver == null ? 0 : ver;
    }
}
