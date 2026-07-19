package com.web.project.auth.service;

import com.web.project.admin.entity.AdminUser;
import com.web.project.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * JWT Token 业务类。
 *
 * 负责根据登录用户信息生成 Token。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /**
     * 为管理员生成 Access Token。
     *
     * @param adminUser 已通过账号密码验证的管理员
     * @return JWT 字符串
     */
    public String createAdminAccessToken(AdminUser adminUser) {
        // 当前时间
        Instant now = Instant.now();

        /*
         * JWT 中保存的信息叫 Claims。
         *
         * 注意：
         * JWT 是签名，不是加密。
         * 不要把密码、身份证、密钥等敏感信息放进去。
         */
        JwtClaimsSet claims = JwtClaimsSet.builder()

                // iss：Token 的签发者
                .issuer(jwtProperties.issuer())

                // sub：Token 对应的主体，这里存管理员 ID
                .subject(adminUser.getId().toString())

                // jti：每一个 Token 的唯一编号
                .id(UUID.randomUUID().toString())

                // iat：Token 的签发时间
                .issuedAt(now)

                // exp：Token 的失效时间
                .expiresAt(now.plus(jwtProperties.accessTokenExpiration()))
                // 自定义字段：管理员账号
                .claim("username", adminUser.getUsername())

                // 自定义字段：用户类型
                .claim("userType", "ADMIN")

                /*
                 * scope 会被 Spring Security
                 * 自动转换成 SCOPE_admin 权限。
                 */
                .claim("scope", "admin")
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
