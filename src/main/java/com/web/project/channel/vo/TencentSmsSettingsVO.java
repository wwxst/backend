package com.web.project.channel.vo;

/** 不包含 SecretKey 的腾讯云短信配置响应。 */
public record TencentSmsSettingsVO(
        String secretId,
        String sdkAppId,
        String signName,
        String templateId,
        String region,
        boolean secretKeyConfigured,
        boolean configured
) {
}
