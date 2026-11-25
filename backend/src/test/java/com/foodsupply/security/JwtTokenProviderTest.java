package com.foodsupply.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private JwtTokenProvider newProvider() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("unit-test-secret-key-0123456789-abcdefghij");
        properties.setExpireMinutes(30);
        JwtTokenProvider provider = new JwtTokenProvider(properties);
        provider.init();
        return provider;
    }

    @Test
    @DisplayName("签发后能解析出用户 ID、用户名和版本号")
    void generateAndParse() {
        JwtTokenProvider provider = newProvider();
        String token = provider.generate(7L, "buyer01", 3);

        Claims claims = provider.parse(token);

        assertEquals(7L, provider.getUserId(claims));
        assertEquals("buyer01", provider.getUsername(claims));
        assertEquals(3, provider.getTokenVersion(claims));
    }

    @Test
    @DisplayName("被篡改的 Token 解析失败")
    void parseTamperedToken() {
        JwtTokenProvider provider = newProvider();
        String token = provider.generate(1L, "admin", 0);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThrows(JwtException.class, () -> provider.parse(tampered));
    }

    @Test
    @DisplayName("密钥不足 32 字节直接启动失败")
    void shortSecretRejected() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("too-short");
        JwtTokenProvider provider = new JwtTokenProvider(properties);

        assertThrows(IllegalStateException.class, provider::init);
    }
}
