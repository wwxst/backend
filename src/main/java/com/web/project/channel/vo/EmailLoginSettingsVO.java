package com.web.project.channel.vo;

/** 不包含 SMTP 密码的邮箱服务配置响应。 */
public record EmailLoginSettingsVO(
        String host,
        int port,
        String username,
        String fromAddress,
        String fromName,
        String security,
        boolean passwordConfigured,
        boolean configured
) {
}
