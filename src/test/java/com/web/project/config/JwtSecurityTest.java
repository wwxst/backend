package com.web.project.config;

import com.web.project.admin.service.SysUserService;
import com.web.project.auth.model.AccountType;
import com.web.project.auth.service.JwtTokenService;
import com.web.project.common.result.PageResult;
import com.web.project.config.properties.JwtProperties;
import com.web.project.user.service.UserProfileService;
import com.web.project.user.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 测试密钥只存在于测试配置，生产配置仍必须显式提供 JWT_SECRET。
@SpringBootTest(properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
class JwtSecurityTest {
    private static final String ADMIN_PATH = "/api/sys-user/sys-users";
    private static final String USER_PATH = "/api/user/me";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired RefreshTokenDecoder refreshTokenDecoder;
    @Autowired JwtProperties properties;
    @MockitoBean SysUserService admins;
    @MockitoBean UserProfileService profiles;

    private String adminAccessToken() {
        return tokens.createAccessToken(AccountType.SYS_USER, 1L, "session-admin", null);
    }

    private String userAccessToken() {
        return tokens.createAccessToken(AccountType.USER, 2L, "session-user", 0L);
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void realAdminTokenCanAccessAdminApiButNotUserApi() throws Exception {
        when(admins.getSysUserPage(any())).thenReturn(new PageResult<>(0L, 1, 10, List.of()));
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        verifyNoInteractions(profiles);
    }

    @Test
    void realUserTokenCanAccessUserApiButNotAdminApi() throws Exception {
        when(profiles.getProfile(2L)).thenReturn(new UserInfoVO(
                2L, "123456", "13800138000", null, "用户", null, 1, LocalDateTime.now()
        ));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(profiles).getProfile(2L);
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        verifyNoInteractions(admins);
    }

    @Test
    void refreshTokenCannotAccessResourceEndpoints() throws Exception {
        // Refresh Token 的 tokenType 为 refresh，资源服务器只接受 access
        String refreshToken = tokens.createRefreshToken(
                AccountType.SYS_USER, 1L, "session-admin", null
        );
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void accessTokenCannotBeUsedAtRefreshEndpoint() throws Exception {
        // Access Token 的 tokenType 为 access，Refresh 入口只接受 refresh
        for (String path : List.of("/api/sys-user/auth/refresh", "/api/user/auth/refresh")) {
            mvc.perform(post(path).contentType("application/json")
                            .content("{\"refreshToken\":\"" + adminAccessToken() + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40104));
        }
    }

    @Test
    void authEndpointsArePublicAndStillValidateInput() throws Exception {
        for (String path : List.of(
                "/api/sys-user/auth/login",
                "/api/sys-user/auth/refresh",
                "/api/user/auth/login",
                "/api/user/auth/refresh",
                "/api/user/auth/register",
                "/api/user/auth/reset-password"
        )) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(admins, profiles);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        assertUnauthorized(encode(encoder, properties.issuer(), Instant.now().minusSeconds(300)));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        assertUnauthorized(encode(encoder, "another-service", Instant.now().plusSeconds(300)));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String otherSecret = Base64.getEncoder().encodeToString(new byte[32]);
        var otherProperties = new JwtProperties(
                properties.issuer(), otherSecret, Duration.ofHours(1), Duration.ofDays(30)
        );
        var config = new JwtConfig();
        var otherEncoder = config.jwtEncoder(config.jwtSecretKey(otherProperties));
        assertUnauthorized(encode(otherEncoder, properties.issuer(), Instant.now().plusSeconds(300)));
    }

    @Test
    void tokenExpirationsMatchConfiguredDurations() {
        // Access Token 默认 30 分钟，Refresh Token 默认 30 天
        var access = jwtDecoder.decode(tokens.createAccessToken(
                AccountType.USER, 2L, "session-user", 0L
        ));
        assertEquals(1800L, Duration.between(
                access.getIssuedAt(), access.getExpiresAt()
        ).getSeconds());

        var refresh = refreshTokenDecoder.decode(tokens.createRefreshToken(
                AccountType.USER, 2L, "session-user", 0L
        ));
        assertEquals(2_592_000L, Duration.between(
                refresh.getIssuedAt(), refresh.getExpiresAt()
        ).getSeconds());
    }

    @Test
    void removedBusinessEndpointsReturnNotFound() throws Exception {
        for (String path : List.of("/api/sys-user/products", "/api/sys-user/products/1/plans",
                "/api/sys-user/redeem-code-batches", "/api/sys-user/redeem-records", "/api/sys-user/user-subscriptions")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + adminAccessToken()))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/sys-user/products").contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + adminAccessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/user/subscription").header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/user/redemptions").contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + userAccessToken()))
                .andExpect(status().isNotFound());
        verifyNoInteractions(admins, profiles);
    }

    private String encode(JwtEncoder signer, String issuer, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("1")
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiresAt)
                .claim("scope", "admin").claim("tokenType", "access").build();
        return signer.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }
}
