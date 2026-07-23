package com.web.project.auth.service;

import com.web.project.admin.entity.AdminUser;
import com.web.project.config.properties.JwtProperties;
import com.web.project.user.entity.UserAccount;
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
 * 负责生成管理员和普通用户的访问令牌。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /**
     * 为管理员生成 Access Token。
     *
     * @param adminUser 已通过身份验证的管理员
     * @return JWT 字符串
     */
    public String createAdminAccessToken(AdminUser adminUser) {
        return createAccessToken(
                adminUser.getId(),
                adminUser.getUsername(),
                "ADMIN",
                "admin"
        );
    }

    /**
     * 为普通用户生成 Access Token。
     *
     * @param userAccount 已通过身份验证的普通用户
     * @return JWT 字符串
     */
    public String createUserAccessToken(UserAccount userAccount) {
        return createAccessToken(
                userAccount.getId(),
                userAccount.getUsername(),
                "USER",
                "user"
        );
    }

    /**
     * 统一生成访问令牌。
     *
     * 管理员和普通用户的 Token 结构基本一致，
     * 只有用户类型和权限范围不同，因此抽成公共方法。
     *
     * @param id       当前账号 ID
     * @param username 登录账号
     * @param userType 用户类型：ADMIN 或 USER
     * @param scope    权限范围：admin 或 user
     * @return JWT 字符串
     */
    private String createAccessToken(
            Long id,
            String username,
            String userType,
            String scope
    ) {
        Instant now = Instant.now();

        /*
         * JWT 是签名数据，不是加密数据。
         * 不要把密码、身份证、密钥等敏感信息放入 Claims。
         */
        JwtClaimsSet claims = JwtClaimsSet.builder()
                // Token 签发者
                .issuer(jwtProperties.issuer())

                // Token 所属账号 ID
                .subject(id.toString())

                // 每个 Token 的唯一编号
                .id(UUID.randomUUID().toString())

                // 签发时间
                .issuedAt(now)

                // 失效时间
                .expiresAt(
                        now.plus(
                                jwtProperties.accessTokenExpiration()
                        )
                )

                // 登录账号
                .claim("username", username)

                // 区分管理员和普通用户
                .claim("userType", userType)

                /*
                 * admin 会转换成 SCOPE_admin；
                 * user 会转换成 SCOPE_user。
                 */
                .claim("scope", scope)

                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }
}
