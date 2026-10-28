package com.web.project.deployment;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The deployment capability is intentionally super-admin only.
 * The browser never receives the GitHub credential or an arbitrary shell command.
 */
@RestController
@RequestMapping("/api/sys-user/deployment")
@RequiredArgsConstructor
public class DeploymentController {
    private final DeploymentService service;

    @GetMapping
    public Result<DeploymentStatus> status(@AuthenticationPrincipal Jwt jwt) {
        requireSuperAdmin(jwt);
        return Result.success(service.status());
    }

    @PostMapping
    public Result<DeploymentStatus> deploy(@AuthenticationPrincipal Jwt jwt) {
        requireSuperAdmin(jwt);
        return Result.success(service.deploy());
    }

    @PostMapping("/rollback")
    public Result<DeploymentStatus> rollback(@AuthenticationPrincipal Jwt jwt) {
        requireSuperAdmin(jwt);
        return Result.success(service.rollback());
    }

    private static void requireSuperAdmin(Jwt jwt) {
        if (jwt == null || !"SYS_USER".equals(jwt.getClaimAsString("accountType"))
                || !"1".equals(jwt.getSubject())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
