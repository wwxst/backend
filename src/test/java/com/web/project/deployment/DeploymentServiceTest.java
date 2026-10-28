package com.web.project.deployment;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploymentServiceTest {
    private final HttpClient client = mock(HttpClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void updaterIsDisabledByDefaultAndNeverCallsGithubOrRedis() {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(false, null), mapper, redis, client);
        DeploymentStatus status = service.status();
        assertFalse(status.configured());
        assertEquals("not_configured", status.state());
        assertThrows(BusinessException.class, service::deploy);
        verifyNoInteractions(client, redis);
    }

    @Test
    void anEmptyTokenCannotEnableUpdater() {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, " "), mapper, redis, client);
        assertFalse(service.status().configured());
        assertThrows(BusinessException.class, service::deploy);
        verifyNoInteractions(client, redis);
    }

    @Test
    void propertyStringNeverExposesGitHubToken() {
        String info = new DeploymentProperties(true, "sensitive-test-credential").toString();
        assertTrue(info.contains("redacted"));
        assertFalse(info.contains("sensitive-test-credential"));
    }
}
