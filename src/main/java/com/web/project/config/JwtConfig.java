package com.web.project.config;

import com.web.project.config.properties.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * JWT 配置类。
 *
 * 负责创建：
 * 1. JWT 签名密钥
 * 2. JWT 编码器
 * 3. JWT 解码器
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    /**
     * 根据 application.yml 中的 Base64 密钥，
     * 创建用于 HS256 签名的 SecretKey。
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties jwtProperties) {
        byte[] secretBytes;
        try {
            // 将 Base64 字符串还原成原始字节
            secretBytes = Base64.getDecoder().decode(jwtProperties.secret());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "app.jwt.secret 必须是正确的 Base64 字符串",
                    exception
            );
        }

        /*
         * HS256 建议至少使用 32 字节，也就是 256 位密钥。
         */
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret 解码后不能少于32字节"
            );
        }

        return new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );
    }

    /**
     * JWT 编码器。
     *
     * 登录成功后，使用它生成并签名 Token。
     */
    @Bean
    public JwtEncoder jwtEncoder(
            SecretKey jwtSecretKey
    ) {
        return NimbusJwtEncoder
                .withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * JWT 解码器。
     *
     * 前端携带 Token 访问接口时，
     * Spring Security 使用它验证 Token。
     */
    @Bean
    public JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            JwtProperties jwtProperties
    ) {
        NimbusJwtDecoder jwtDecoder =
                NimbusJwtDecoder
                        .withSecretKey(jwtSecretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();

        /*
         * 校验 Token 的签发者和标准时间字段。
         * 包括 iss、exp、nbf 等。
         */
        jwtDecoder.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(
                        jwtProperties.issuer()
                )
        );

        return jwtDecoder;
    }
}
