package com.web.project.deployment;

/** Last GitHub Actions production deployment, not a local process status. */
public record DeploymentStatus(
        boolean configured,
        String state,
        String conclusion,
        String runUrl,
        String title,
        String requestId,
        boolean rollbackAvailable
) {
    public DeploymentStatus(boolean configured, String state, String conclusion,
                            String runUrl, String title, String requestId) {
        this(configured, state, conclusion, runUrl, title, requestId, false);
    }
}
