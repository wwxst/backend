package com.web.project.auth.code;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 阿里云短信发送适配器，短信模板变量固定使用 code。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AliyunSmsGateway {

    private final LoginProviderSettingsService settingsService;

    public void send(String phone, String code) {
        var settings = settingsService.requireAliyunSmsConfig();
        try {
            Config config = new Config()
                    .setAccessKeyId(settings.accessKeyId())
                    .setAccessKeySecret(settings.accessKeySecret());
            config.endpoint = "dysmsapi.aliyuncs.com";

            Client client = new Client(config);
            SendSmsRequest request = new SendSmsRequest()
                    .setPhoneNumbers(phone)
                    .setSignName(settings.signName())
                    .setTemplateCode(settings.templateCode())
                    .setTemplateParam("{\"code\":\"" + code + "\"}");
            SendSmsResponse response = client.sendSms(request);
            if (response.getBody() == null || !"OK".equalsIgnoreCase(response.getBody().getCode())) {
                throw new BusinessException(ErrorCode.CODE_DELIVERY_FAILED);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Aliyun SMS delivery failed, exception={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.CODE_DELIVERY_FAILED);
        }
    }
}
