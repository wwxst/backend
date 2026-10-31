package com.web.project.auth.code;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.sms.v20210111.SmsClient;
import com.tencentcloudapi.sms.v20210111.models.SendSmsRequest;
import com.tencentcloudapi.sms.v20210111.models.SendSmsResponse;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 腾讯云短信发送适配器；模板第一个参数为验证码。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TencentSmsGateway {

    private final LoginProviderSettingsService settingsService;

    public void send(String phone, String code) {
        var settings = settingsService.requireTencentSmsConfig();
        try {
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("sms.tencentcloudapi.com");
            httpProfile.setConnTimeout(10);
            httpProfile.setReadTimeout(10);
            httpProfile.setWriteTimeout(10);

            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            SmsClient client = new SmsClient(
                    new Credential(settings.secretId(), settings.secretKey()),
                    settings.region(),
                    clientProfile
            );

            SendSmsRequest request = new SendSmsRequest();
            request.setSmsSdkAppId(settings.sdkAppId());
            request.setSignName(settings.signName());
            request.setTemplateId(settings.templateId());
            request.setTemplateParamSet(new String[]{code});
            request.setPhoneNumberSet(new String[]{"+86" + phone});

            SendSmsResponse response = client.SendSms(request);
            var statuses = response.getSendStatusSet();
            if (statuses == null || statuses.length == 0
                    || !"Ok".equalsIgnoreCase(statuses[0].getCode())) {
                throw new BusinessException(ErrorCode.CODE_DELIVERY_FAILED);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Tencent Cloud SMS delivery failed, exception={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.CODE_DELIVERY_FAILED);
        }
    }
}
