package com.web.project.auth.protection;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 仅对配置明确的直接代理读取客户端转发地址。 */
@Component
public class ClientIpResolver {

    private final LoginProtectionProperties properties;

    public ClientIpResolver(LoginProtectionProperties properties) {
        this.properties = properties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!properties.trustedProxyAddresses().contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        return Arrays.stream(forwardedFor.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElse(remoteAddress);
    }
}
