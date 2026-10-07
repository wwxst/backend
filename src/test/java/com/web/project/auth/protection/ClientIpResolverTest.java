package com.web.project.auth.protection;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderFromUntrustedPeer() {
        HttpServletRequest request = request("10.0.0.9", "203.0.113.10");
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.8")));

        assertEquals("10.0.0.9", resolver.resolve(request));
    }

    @Test
    void usesFirstForwardedAddressFromConfiguredProxy() {
        HttpServletRequest request = request("10.0.0.8", "203.0.113.10, 10.0.0.7");
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.8")));

        assertEquals("203.0.113.10", resolver.resolve(request));
    }

    @Test
    void fallsBackToProxyAddressWhenForwardedHeaderIsMissing() {
        HttpServletRequest request = request("10.0.0.8", null);
        ClientIpResolver resolver = new ClientIpResolver(properties(List.of("10.0.0.8")));

        assertEquals("10.0.0.8", resolver.resolve(request));
    }

    private static HttpServletRequest request(String remoteAddress, String forwardedFor) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        when(request.getHeader("X-Forwarded-For")).thenReturn(forwardedFor);
        return request;
    }

    private static LoginProtectionProperties properties(List<String> trustedProxies) {
        return new LoginProtectionProperties(1, Duration.ofMinutes(1), 60, 20, trustedProxies);
    }
}
