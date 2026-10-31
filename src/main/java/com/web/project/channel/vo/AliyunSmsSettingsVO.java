package com.web.project.channel.vo;

/** 不包含 AccessKey Secret 的阿里云短信配置响应。 */
public record AliyunSmsSettingsVO(
        String accessKeyId,
        String signName,
        String templateCode,
        boolean accessKeySecretConfigured,
        boolean configured
) {
}
