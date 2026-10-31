package com.web.project.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 腾讯云短信服务配置更新参数；密钥留空时保留已保存的密钥。 */
public record TencentSmsSettingsUpdateDTO(
        @NotBlank(message = "请输入 SecretId") @Size(max = 100) String secretId,
        @Size(max = 255) String secretKey,
        @NotBlank(message = "请输入短信 SDK App ID") @Size(max = 32) String sdkAppId,
        @NotBlank(message = "请输入短信签名") @Size(max = 50) String signName,
        @NotBlank(message = "请输入短信模板 ID") @Size(max = 64) String templateId,
        @NotBlank(message = "请选择腾讯云地域") @Size(max = 32) String region
) {
}
