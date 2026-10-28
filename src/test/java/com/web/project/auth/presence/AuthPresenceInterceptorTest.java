package com.web.project.auth.presence;

import com.web.project.auth.model.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthPresenceInterceptorTest {
    private final AuthPresenceStore store = mock(AuthPresenceStore.class);
    private final AuthPresenceInterceptor interceptor = new AuthPresenceInterceptor(store);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tracksOnlyAuthenticatedJwtTraffic() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(store);

        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("9")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("accountType", "USER")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verify(store).touch(AccountType.USER, 9L);
    }
}
