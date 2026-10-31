package com.web.project.channel.model;

import com.web.project.auth.model.TargetType;

/**
 * 登录基础配置。登录开关只控制登录，不影响注册、密码重置和账号换绑。
 *
 * @param phoneLoginEnabled 是否允许手机号登录
 * @param emailLoginEnabled 是否允许邮箱登录
 * @param smsProvider       当前选用的短信服务平台
 */
public record AuthChannelConfig(
        boolean phoneLoginEnabled,
        boolean emailLoginEnabled,
        String smsProvider
) {
    public boolean loginEnabled(TargetType targetType) {
        return targetType == TargetType.PHONE ? phoneLoginEnabled : emailLoginEnabled;
    }

    @Override
    public String toString() {
        return "LoginSettings[phoneLoginEnabled=" + phoneLoginEnabled
                + ", emailLoginEnabled=" + emailLoginEnabled
                + ", smsProvider=" + smsProvider + "]";
    }
}
