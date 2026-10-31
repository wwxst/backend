package com.web.project.channel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 阿里云短信服务配置更新参数；密钥留空时保留已保存的密钥。 */
public record AliyunSmsSettingsUpdateDTO(
        @NotBlank(message = "请输入 AccessKey ID") @Size(max = 100) String accessKeyId,
        @Size(max = 255) String accessKeySecret,
        @NotBlank(message = "请输入短信签名") @Size(max = 50) String signName,
        @NotBlank(message = "请输入短信模板 Code") @Size(max = 64) String templateCode
) {
}
