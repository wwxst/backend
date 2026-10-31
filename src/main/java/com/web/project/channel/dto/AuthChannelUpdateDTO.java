package com.web.project.channel.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 登录基础配置修改参数。
 *
 * @param phoneLoginEnabled 是否允许手机号登录
 * @param emailLoginEnabled 是否允许邮箱登录
 * @param smsProvider       当前选用的短信服务平台
 */
public record AuthChannelUpdateDTO(
        @NotNull(message = "请选择是否启用手机登录")
        Boolean phoneLoginEnabled,

        @NotNull(message = "请选择是否启用邮箱登录")
        Boolean emailLoginEnabled,

        @NotNull(message = "请选择短信服务平台")
        @Pattern(regexp = "aliyun|tencent", message = "短信服务平台不正确")
        String smsProvider
) {
}
