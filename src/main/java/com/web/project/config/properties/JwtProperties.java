package com.web.project.config.properties;


import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置属性。
 *
 * Spring 会把 application.yml 中 app.jwt 下面的配置，
 * 自动绑定到这个 record 中。
 *
 * @param issuer                Token 签发者
 * @param secret                Base64 格式的签名密钥
 * @param accessTokenExpiration Access Token 有效期
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        Duration accessTokenExpiration
) {

}
