package com.web.project.deployment;

/** Last GitHub Actions production deployment, not a local process status. */
public record DeploymentStatus(
        boolean configured,
        String state,
        String conclusion,
        String runUrl,
        String title,
        String requestId
) {
}
