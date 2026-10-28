package com.web.project.deployment;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.stream.Stream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Fixed GitHub Actions dispatch: no caller-supplied repository, branch,
 * workflow, command, or server address. The GitHub credential stays server-side.
 */
@Slf4j
@Service
public class DeploymentService {
    private static final URI WORKFLOW = URI.create(
            "https://api.github.com/repos/wwxst/atlas-console/actions/workflows/deploy-production.yml");
    private static final URI RUNS = URI.create(
            WORKFLOW + "/runs?event=workflow_dispatch&branch=main&per_page=1");
    private static final String DISPATCH_LOCK = "atlas:deploy:dispatch-lock";

    private final DeploymentProperties properties;
    private final ObjectMapper mapper;
    private final StringRedisTemplate redis;
    private final HttpClient client;

    @Autowired
    public DeploymentService(DeploymentProperties properties, ObjectMapper mapper, StringRedisTemplate redis) {
        this(properties, mapper, redis, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
    }

    /** Injectable HTTP transport for tests; never exposed through the controller. */
    DeploymentService(DeploymentProperties properties, ObjectMapper mapper,
                      StringRedisTemplate redis, HttpClient client) {
        this.properties = properties;
        this.mapper = mapper;
        this.redis = redis;
        this.client = client;
    }

    public DeploymentStatus status() {
        if (!properties.configured()) {
            return new DeploymentStatus(false, "not_configured", null, null, null, null);
        }

        HttpResponse<String> response = send(request(RUNS).GET().build());
        if (response.statusCode() != 200) {
            throw unavailable();
        }

        try {
            JsonNode runs = mapper.readTree(response.body()).path("workflow_runs");
            if (!runs.isArray() || runs.isEmpty()) {
                return new DeploymentStatus(true, "not_started", null, null, null, null, rollbackAvailable());
            }
            JsonNode latest = runs.get(0);
            return new DeploymentStatus(true,
                    latest.path("status").asText("unknown"),
                    latest.path("conclusion").isNull() ? null
                            : latest.path("conclusion").asText(null),
                    latest.path("html_url").asText(null),
                    latest.path("display_title").asText(null),
                    null, rollbackAvailable());
        } catch (RuntimeException exception) {
            // GitHub error details may contain sensitive request metadata; do not relay.
            throw unavailable();
        }
    }

    public DeploymentStatus deploy() {
        return dispatch("deploy");
    }

    /** Only releases recorded on the production server may be chosen. */
    public List<ReleaseHistoryItem> history() {
        if (!properties.configured()) {
            return List.of();
        }
        Path directory = Path.of(properties.stateDirectory()).resolve("history");
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        String currentId = manifestId(Path.of(properties.stateDirectory()).resolve("current.env"));
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> readRelease(path, currentId))
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(ReleaseHistoryItem::deployedAt).reversed())
                    .limit(50)
                    .toList();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    public DeploymentStatus rollbackHistorical(String releaseId) {
        if (releaseId == null || !releaseId.matches("[0-9a-f]{40}_[0-9a-f]{40}")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "历史版本编号无效");
        }
        boolean allowed = history().stream().anyMatch(item ->
                item.releaseId().equals(releaseId) && !item.current());
        if (!allowed) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该历史版本不可回滚");
        }
        return dispatch("rollback-history", releaseId);
    }

    private ReleaseHistoryItem readRelease(Path path, String currentId) {
        String filename = path.getFileName().toString();
        if (!filename.matches("[0-9a-f]{40}_[0-9a-f]{40}\\.env")) {
            return null;
        }
        String releaseId = filename.substring(0, filename.length() - 4);
        if (!releaseId.equals(manifestId(path))) {
            return null;
        }
        try {
            return new ReleaseHistoryItem(releaseId,
                    releaseId.substring(0, 40),
                    releaseId.substring(41),
                    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant(),
                    releaseId.equals(currentId));
        } catch (IOException exception) {
            return null;
        }
    }

    private String manifestId(Path path) {
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.size() != 2
                    || !lines.get(0).matches("frontend=git-[0-9a-f]{40}")
                    || !lines.get(1).matches("backend=git-[0-9a-f]{40}")) {
                return null;
            }
            return lines.get(0).substring("frontend=git-".length()) + "_"
                    + lines.get(1).substring("backend=git-".length());
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    public DeploymentStatus rollback() {
        if (!rollbackAvailable()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有可回滚的上一版本");
        }
        return dispatch("rollback");
    }

    private boolean rollbackAvailable() {
        try {
            Path directory = Path.of(properties.stateDirectory());
            for (String file : List.of("current.env", "previous.env")) {
                List<String> lines = Files.readAllLines(directory.resolve(file));
                if (lines.size() != 2
                        || !lines.get(0).matches("frontend=git-[0-9a-f]{40}")
                        || !lines.get(1).matches("backend=git-[0-9a-f]{40}")) {
                    return false;
                }
            }
            return true;
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    private DeploymentStatus dispatch(String operation) {
        return dispatch(operation, null);
    }

    private DeploymentStatus dispatch(String operation, String releaseId) {
        if (!properties.configured()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "尚未配置服务器与 GitHub Actions，暂不能自动更新");
        }

        Boolean locked = redis.opsForValue().setIfAbsent(
                DISPATCH_LOCK, "1", Duration.ofSeconds(90));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "更新请求已提交，请稍后查看执行状态");
        }

        boolean accepted = false;
        try {
            DeploymentStatus last = status();
            if ("in_progress".equals(last.state()) || "queued".equals(last.state())
                    || "waiting".equals(last.state()) || "pending".equals(last.state())
                    || "requested".equals(last.state())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已有更新任务正在运行");
            }
            String requestId = UUID.randomUUID().toString();
            Map<String, String> inputs = new java.util.HashMap<>();
            inputs.put("request_id", requestId);
            inputs.put("operation", operation);
            if (releaseId != null) {
                inputs.put("target_release", releaseId);
            }
            String body = mapper.writeValueAsString(Map.of("ref", "main", "inputs", inputs));
            HttpResponse<String> response = send(request(
                    URI.create(WORKFLOW + "/dispatches"))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json").build());
            if (response.statusCode() != 204) {
                throw unavailable();
            }
            accepted = true;
            log.info("Production release workflow requested; operation={}; requestId={}", operation, requestId);
            return new DeploymentStatus(true, "requested", null,
                    "https://github.com/wwxst/atlas-console/actions/workflows/deploy-production.yml",
                    "Atlas Deploy " + requestId, requestId);
        } finally {
            if (!accepted) {
                // Retain the short lock after success while GitHub indexes the new run.
                redis.delete(DISPATCH_LOCK);
            }
        }
    }

    private HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + properties.githubToken())
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "atlas-console-deployment");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "GitHub 更新服务暂不可用，请稍后再试");
    }
}
