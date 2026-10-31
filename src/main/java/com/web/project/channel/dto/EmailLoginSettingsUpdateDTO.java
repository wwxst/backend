package com.web.project.channel.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 邮箱 SMTP 配置更新参数；密码留空时保留已保存的密码。 */
public record EmailLoginSettingsUpdateDTO(
        @NotBlank(message = "请输入 SMTP 服务器地址") @Size(max = 200) String host,
        @Min(value = 1, message = "SMTP 端口不正确") @Max(value = 65535, message = "SMTP 端口不正确") int port,
        @NotBlank(message = "请输入 SMTP 用户名") @Size(max = 150) String username,
        @Size(max = 255) String password,
        @NotBlank(message = "请输入发件邮箱") @Email(message = "发件邮箱格式不正确") @Size(max = 150) String fromAddress,
        @NotBlank(message = "请输入发件人名称") @Size(max = 80) String fromName,
        @NotBlank(message = "请选择 SMTP 加密方式") @Pattern(regexp = "SSL|STARTTLS|NONE", message = "SMTP 加密方式不正确") String security
) {
}
