package com.web.project.deployment;

import com.web.project.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;

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
    @SuppressWarnings("unchecked")
    void statusReadsOnlyFixedProductionWorkflowAndSanitizesResponse() throws Exception {
        HttpResponse<String> reply = mock(HttpResponse.class);
        when(reply.statusCode()).thenReturn(200);
        when(reply.body()).thenReturn("{\"workflow_runs\":[{\"status\":\"completed\","
                + "\"conclusion\":\"success\",\"display_title\":\"Atlas Deploy abc\","
                + "\"html_url\":\"https://github.com/wwxst/atlas-console/actions/runs/42\"}]}");
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(reply);
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-gh-token"), mapper, redis, client);

        DeploymentStatus status = service.status();

        assertTrue(status.configured());
        assertEquals("completed", status.state());
        assertEquals("success", status.conclusion());
        org.mockito.ArgumentCaptor<HttpRequest> request =
                org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(request.capture(),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("api.github.com", request.getValue().uri().getHost());
        assertTrue(request.getValue().uri().getPath().endsWith("/deploy-production.yml/runs"));
        assertEquals("Bearer test-gh-token",
                request.getValue().headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    @SuppressWarnings("unchecked")
    void dispatchUsesMainAndRejectsImmediateDuplicate() throws Exception {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("atlas:deploy:dispatch-lock", "1", Duration.ofSeconds(90)))
                .thenReturn(true, false);
        HttpResponse<String> latest = mock(HttpResponse.class);
        when(latest.statusCode()).thenReturn(200);
        when(latest.body()).thenReturn("{\"workflow_runs\":[]}");
        HttpResponse<String> created = mock(HttpResponse.class);
        when(created.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, created);
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-gh-token"), mapper, redis, client);

        DeploymentStatus started = service.deploy();
        assertEquals("requested", started.state());
        assertNotNull(started.requestId());
        assertThrows(BusinessException.class, service::deploy);
        verify(client, times(2)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void rollbackRequiresPreviousReleaseAndIgnoresMalformedManifest(@TempDir Path state) throws Exception {
        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-token", state.toString()), mapper, redis, client);
        assertThrows(BusinessException.class, service::rollback);
        verifyNoInteractions(redis, client);

        String valid = "frontend=git-" + "a".repeat(40) + "\n"
                + "backend=git-" + "b".repeat(40) + "\n";
        Files.writeString(state.resolve("current.env"), valid);
        Files.writeString(state.resolve("previous.env"), "invalid content");
        assertThrows(BusinessException.class, service::rollback);
        verifyNoInteractions(redis, client);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rollbackUsesFixedWorkflowWhenValidPreviousReleaseExists(@TempDir Path state) throws Exception {
        String previous = "frontend=git-" + "a".repeat(40) + "\n"
                + "backend=git-" + "b".repeat(40) + "\n";
        Files.writeString(state.resolve("current.env"), previous);
        Files.writeString(state.resolve("previous.env"), previous);

        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent("atlas:deploy:dispatch-lock", "1", Duration.ofSeconds(90)))
                .thenReturn(true);
        HttpResponse<String> latest = mock(HttpResponse.class);
        when(latest.statusCode()).thenReturn(200);
        when(latest.body()).thenReturn("{\"workflow_runs\":[]}");
        HttpResponse<String> accepted = mock(HttpResponse.class);
        when(accepted.statusCode()).thenReturn(204);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, accepted);

        DeploymentService service = new DeploymentService(
                new DeploymentProperties(true, "test-token", state.toString()), mapper, redis, client);
        assertTrue(service.status().rollbackAvailable());
        reset(client);
        when(client.send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(latest, accepted);
        assertEquals("requested", service.rollback().state());
        verify(client, times(2)).send(any(HttpRequest.class),
                org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }

    @Test
    void propertyStringNeverExposesGitHubToken() {
        String info = new DeploymentProperties(true, "sensitive-test-credential").toString();
        assertTrue(info.contains("redacted"));
        assertFalse(info.contains("sensitive-test-credential"));
    }
}
