package com.foodsupply.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置。secret 不写在 application.yml 里，放本地配置，避免进仓库。
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** 签名密钥，HMAC-SHA 要求至少 32 字节 */
    private String secret;

    /** 有效期，单位分钟 */
    private long expireMinutes = 120;
}
