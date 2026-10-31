package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;
import com.web.project.channel.service.AuthChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 根据登录基础配置选择短信平台，邮箱验证码统一走 SMTP。 */
@Component
@RequiredArgsConstructor
public class ConfiguredCodeSender implements CodeSender {

    private final AuthChannelService authChannelService;
    private final AliyunSmsGateway aliyunSmsGateway;
    private final TencentSmsGateway tencentSmsGateway;
    private final SmtpCodeSender smtpCodeSender;

    @Override
    public void send(TargetType targetType, String target, String code) {
        if (targetType == TargetType.EMAIL) {
            smtpCodeSender.send(target, code);
            return;
        }

        String provider = authChannelService.getChannels().smsProvider();
        switch (provider) {
            case "aliyun" -> aliyunSmsGateway.send(target, code);
            case "tencent" -> tencentSmsGateway.send(target, code);
            default -> throw new IllegalStateException("未知短信服务平台");
        }
    }
}
