package com.web.project.config;

import com.web.project.admin.entity.SysUser;
import com.web.project.auth.service.JwtTokenService;
import com.web.project.auth.service.UserAuthService;
import com.web.project.config.properties.JwtProperties;
import com.web.project.admin.service.SysUserService;
import com.web.project.common.result.PageResult;
import com.web.project.user.entity.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 测试密钥只存在于测试配置，生产配置仍必须显式提供 JWT_SECRET。
@SpringBootTest(properties = "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureMockMvc
class JwtSecurityTest {
    private static final String ADMIN_PATH = "/api/sys-user/sys-users";
    private static final String USER_PATH = "/api/user/auth/me";

    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired JwtProperties properties;
    @MockitoBean SysUserService admins;
    @MockitoBean UserAuthService users;

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get(ADMIN_PATH)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }

    @Test
    void realAdminTokenCanAccessAdminApiButNotUserApi() throws Exception {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        String token = tokens.createSysUserAccessToken(admin);
        when(admins.getSysUserPage(any())).thenReturn(new PageResult<>(0L, 1, 10, List.of()));
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        verifyNoInteractions(users);
    }

    @Test
    void realUserTokenCanAccessUserApiButNotAdminApi() throws Exception {
        UserAccount user = new UserAccount();
        user.setId(2L);
        user.setUsername("user");
        String token = tokens.createUserAccessToken(user);
        mvc.perform(get(USER_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        verify(users).getCurrentUser(2L);
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(40301));
        verifyNoInteractions(admins);
    }

    @Test
    void loginEndpointsArePublicAndStillValidateInput() throws Exception {
        for (String path : List.of("/api/sys-user/auth/login", "/api/user/auth/login")) {
            mvc.perform(post(path).contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(users);
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
    void removedBusinessEndpointsReturnNotFound() throws Exception {
        SysUser admin = new SysUser();
        admin.setId(1L);
        admin.setUsername("admin");
        String adminToken = tokens.createSysUserAccessToken(admin);
        for (String path : List.of("/api/sys-user/products", "/api/sys-user/products/1/plans",
                "/api/sys-user/redeem-code-batches", "/api/sys-user/redeem-records", "/api/sys-user/user-subscriptions")) {
            mvc.perform(get(path).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/sys-user/products").contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
        UserAccount user = new UserAccount();
        user.setId(2L);
        user.setUsername("user");
        String userToken = tokens.createUserAccessToken(user);
        mvc.perform(get("/api/user/subscription").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/user/redemptions").contentType("application/json").content("{}")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());
        verifyNoInteractions(admins, users);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        String otherSecret = Base64.getEncoder().encodeToString(new byte[32]);
        var otherProperties = new JwtProperties(properties.issuer(), otherSecret, Duration.ofHours(1));
        var config = new JwtConfig();
        var otherEncoder = config.jwtEncoder(config.jwtSecretKey(otherProperties));
        assertUnauthorized(encode(otherEncoder, properties.issuer(), Instant.now().plusSeconds(300)));
    }

    private String encode(JwtEncoder signer, String issuer, Instant expiresAt) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject("1")
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiresAt)
                .claim("scope", "admin").build();
        return signer.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(40102));
        verifyNoInteractions(admins);
    }
}
