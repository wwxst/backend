package com.web.project.config;

import com.web.project.config.properties.JwtProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {
    @Test
    void missingMalformedAndShortSecretsFailBeforeTokenIssuance() {
        for (String secret : new String[]{null, "", " ", "not-base64!", "c2hvcnQ="}) {
            var properties = new JwtProperties("web-project", secret, Duration.ofHours(2));
            assertThrows(IllegalStateException.class, () -> new JwtConfig().jwtSecretKey(properties));
        }
    }
}
