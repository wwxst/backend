package com.web.project.auth.service;

import com.web.project.auth.model.AccountType;
import com.web.project.auth.model.TokenType;
import com.web.project.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT Token 业务类。
 *
 * 统一为两类账号签发 Access Token 和 Refresh Token。
 * 公共 claims 固定为 sub、accountType、sessionId、scope、tokenType；
 * 普通用户额外携带 passwordVersion，系统用户当前没有密码版本字段。
 */
@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    /**
     * 签发 Access Token。
     *
     * @param accountType     账号类型
     * @param accountId       账号主键，写入 sub
     * @param sessionId       当前登录会话 ID
     * @param passwordVersion 普通用户密码版本；系统用户传 null
     * @return Access Token 字符串
     */
    public String createAccessToken(
            AccountType accountType,
            Long accountId,
            String sessionId,
            Long passwordVersion
    ) {
        return createToken(
                accountType,
                accountId,
                sessionId,
                TokenType.ACCESS,
                passwordVersion,
                jwtProperties.accessTokenExpiration()
        );
    }

    /**
     * 签发 Refresh Token。
     *
     * @param accountType     账号类型
     * @param accountId       账号主键，写入 sub
     * @param sessionId       当前登录会话 ID
     * @param passwordVersion 普通用户密码版本；系统用户传 null
     * @return Refresh Token 字符串
     */
    public String createRefreshToken(
            AccountType accountType,
            Long accountId,
            String sessionId,
            Long passwordVersion
    ) {
        return createToken(
                accountType,
                accountId,
                sessionId,
                TokenType.REFRESH,
                passwordVersion,
                jwtProperties.refreshTokenExpiration()
        );
    }

    /**
     * 按方案规定的 claims 生成 Token。
     *
     * JWT 是签名数据而不是加密数据，因此不放入密码等敏感信息。
     */
    private String createToken(
            AccountType accountType,
            Long accountId,
            String sessionId,
            TokenType tokenType,
            Long passwordVersion,
            Duration expiration
    ) {
        Instant now = Instant.now();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                // sub 保存账号主键，是两个 /me 接口读取账号 ID 的唯一来源
                .subject(accountId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(expiration))
                .claim("accountType", accountType.name())
                .claim("sessionId", sessionId)
                // Spring Security 将 scope 映射为 SCOPE_ 权限。
                .claim("scope", accountType.scope())
                // 解码入口据此拒绝 Access 与 Refresh 类型混用。
                .claim("tokenType", tokenType.value());

        // 普通用户额外携带密码版本；系统用户不携带该 claim
        if (passwordVersion != null) {
            claims.claim("passwordVersion", passwordVersion);
        }

        return jwtEncoder
                .encode(JwtEncoderParameters.from(claims.build()))
                .getTokenValue();
    }
}
