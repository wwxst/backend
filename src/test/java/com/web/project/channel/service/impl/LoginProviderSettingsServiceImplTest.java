package com.web.project.channel.service.impl;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.service.CredentialCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginProviderSettingsServiceImplTest {

    private final SysConfigMapper mapper = mock(SysConfigMapper.class);
    private final CredentialCipher cipher = new CredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32])
    );
    private final LoginProviderSettingsServiceImpl service =
            new LoginProviderSettingsServiceImpl(mapper, cipher);

    private final Map<String, String> defaults = Map.ofEntries(
            Map.entry("auth.sms.aliyun.access-key-id", ""),
            Map.entry("auth.sms.aliyun.access-key-secret", ""),
            Map.entry("auth.sms.aliyun.sign-name", ""),
            Map.entry("auth.sms.aliyun.template-code", ""),
            Map.entry("auth.sms.tencent.secret-id", ""),
            Map.entry("auth.sms.tencent.secret-key", ""),
            Map.entry("auth.sms.tencent.sdk-app-id", ""),
            Map.entry("auth.sms.tencent.sign-name", ""),
            Map.entry("auth.sms.tencent.template-id", ""),
            Map.entry("auth.sms.tencent.region", "ap-guangzhou"),
            Map.entry("auth.email.smtp.host", ""),
            Map.entry("auth.email.smtp.port", "465"),
            Map.entry("auth.email.smtp.username", ""),
            Map.entry("auth.email.smtp.password", ""),
            Map.entry("auth.email.smtp.from-address", ""),
            Map.entry("auth.email.smtp.from-name", "Atlas"),
            Map.entry("auth.email.smtp.security", "SSL")
    );

    @BeforeEach
    void setUp() {
        when(mapper.selectByKeys(anyList())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            return keys.stream().map(key -> config(key, defaults.get(key))).toList();
        });
        when(mapper.updateValue(anyString(), anyString())).thenReturn(1);
    }

    private SysConfig config(String key, String value) {
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }

    @Test
    void savesAliyunSecretEncryptedAndNeverReturnsIt() {
        service.updateAliyunSmsSettings(new AliyunSmsSettingsUpdateDTO(
                "access-id", "access-secret", "Atlas", "SMS_123"
        ));

        ArgumentCaptor<String> encrypted = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateValue(eq("auth.sms.aliyun.access-key-secret"), encrypted.capture());
        assertNotEquals("access-secret", encrypted.getValue());
        assertEquals("access-secret", cipher.decrypt(encrypted.getValue()));

        when(mapper.selectByKeys(anyList())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            return keys.stream().map(key -> config(
                    key,
                    key.equals("auth.sms.aliyun.access-key-id") ? "access-id"
                            : key.equals("auth.sms.aliyun.access-key-secret") ? encrypted.getValue()
                            : key.equals("auth.sms.aliyun.sign-name") ? "Atlas"
                            : key.equals("auth.sms.aliyun.template-code") ? "SMS_123" : ""
            )).toList();
        });
        var response = service.getAliyunSmsSettings();
        assertTrue(response.configured());
        assertTrue(response.accessKeySecretConfigured());
        assertEquals("access-id", response.accessKeyId());
        assertFalse(response.toString().contains("access-secret"));
    }

    @Test
    void blankSecretKeepsPreviouslyEncryptedValue() {
        String saved = cipher.encrypt("existing-secret");
        when(mapper.selectByKeys(anyList())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            return keys.stream().map(key -> config(
                    key,
                    key.equals("auth.sms.aliyun.access-key-secret") ? saved : defaults.get(key)
            )).toList();
        });

        service.updateAliyunSmsSettings(new AliyunSmsSettingsUpdateDTO(
                "access-id", "", "Atlas", "SMS_123"
        ));

        verify(mapper).updateValue("auth.sms.aliyun.access-key-secret", saved);
    }

    @Test
    void refusesToSendWhenSelectedProviderIsNotConfigured() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                service::requireAliyunSmsConfig
        );
        assertEquals(ErrorCode.LOGIN_PROVIDER_NOT_CONFIGURED, exception.getErrorCode());
    }

    @Test
    void savesEmailPasswordEncryptedAndReturnsOnlyConfiguredFlag() {
        service.updateEmailSettings(new EmailLoginSettingsUpdateDTO(
                "smtp.example.com", 465, "sender@example.com", "smtp-secret",
                "sender@example.com", "Atlas", "SSL"
        ));

        ArgumentCaptor<String> encrypted = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateValue(eq("auth.email.smtp.password"), encrypted.capture());
        assertEquals("smtp-secret", cipher.decrypt(encrypted.getValue()));

        when(mapper.selectByKeys(anyList())).thenAnswer(invocation -> {
            List<String> keys = invocation.getArgument(0);
            return keys.stream().map(key -> config(key, switch (key) {
                case "auth.email.smtp.host" -> "smtp.example.com";
                case "auth.email.smtp.port" -> "465";
                case "auth.email.smtp.username" -> "sender@example.com";
                case "auth.email.smtp.password" -> encrypted.getValue();
                case "auth.email.smtp.from-address" -> "sender@example.com";
                case "auth.email.smtp.from-name" -> "Atlas";
                case "auth.email.smtp.security" -> "SSL";
                default -> "";
            })).toList();
        });
        var response = service.getEmailSettings();
        assertTrue(response.configured());
        assertTrue(response.passwordConfigured());
        assertEquals(465, response.port());
        assertFalse(response.toString().contains("smtp-secret"));
    }
}
