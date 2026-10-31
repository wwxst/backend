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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 阿里云短信发送适配器，复用客户端并在凭据轮换后重建。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AliyunSmsGateway {

    private final LoginProviderSettingsService settingsService;
    private volatile Client cachedClient;
    private volatile String cachedFingerprint;

    public void send(String phone, String code) {
        var settings = settingsService.requireAliyunSmsConfig();
        try {
            Client client = clientFor(settings);
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

    private Client clientFor(LoginProviderSettingsService.AliyunSmsConfig settings) throws Exception {
        String fingerprint = fingerprint(settings.accessKeyId().length() + ":" + settings.accessKeyId() + settings.accessKeySecret());
        Client current = cachedClient;
        if (current != null && fingerprint.equals(cachedFingerprint)) return current;

        synchronized (this) {
            current = cachedClient;
            if (current != null && fingerprint.equals(cachedFingerprint)) return current;

            Config config = new Config()
                    .setAccessKeyId(settings.accessKeyId())
                    .setAccessKeySecret(settings.accessKeySecret())
                    .setConnectTimeout(5000)
                    .setReadTimeout(5000);
            config.endpoint = "dysmsapi.aliyuncs.com";
            Client created = new Client(config);
            cachedFingerprint = fingerprint;
            cachedClient = created;
            return created;
        }
    }

    private String fingerprint(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
