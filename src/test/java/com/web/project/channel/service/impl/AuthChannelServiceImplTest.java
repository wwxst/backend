package com.web.project.channel.service.impl;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AuthChannelConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthChannelServiceImplTest {
    private final SysConfigMapper sysConfigMapper = mock(SysConfigMapper.class);
    private final AuthChannelServiceImpl service = new AuthChannelServiceImpl(sysConfigMapper);

    private SysConfig config(String key, String value) {
        SysConfig config = new SysConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        return config;
    }

    @Test
    void readsLoginSwitchesAndSelectedSmsProvider() {
        when(sysConfigMapper.selectByKeys(any())).thenReturn(List.of(
                config("auth.phone.login.enabled", "true"),
                config("auth.email.login.enabled", "false"),
                config("auth.sms.provider", "tencent")
        ));
        AuthChannelConfig settings = service.getChannels();
        assertTrue(settings.phoneLoginEnabled());
        assertFalse(settings.emailLoginEnabled());
        assertEquals("tencent", settings.smsProvider());
    }

    @Test
    void allowsBothLoginMethodsToBeDisabled() {
        when(sysConfigMapper.selectLoginSettingsForUpdate()).thenReturn(List.of(
                config("auth.phone.login.enabled", "true"),
                config("auth.email.login.enabled", "true"),
                config("auth.sms.provider", "aliyun")
        ));
        when(sysConfigMapper.updateValue(anyString(), anyString())).thenReturn(1);
        service.updateSettings(new AuthChannelUpdateDTO(false, false, "tencent"));
        verify(sysConfigMapper).updateValue("auth.phone.login.enabled", "false");
        verify(sysConfigMapper).updateValue("auth.email.login.enabled", "false");
        verify(sysConfigMapper).updateValue("auth.sms.provider", "tencent");
    }

    @Test
    void rejectsMalformedPersistedProvider() {
        when(sysConfigMapper.selectByKeys(any())).thenReturn(List.of(
                config("auth.phone.login.enabled", "true"),
                config("auth.email.login.enabled", "true"),
                config("auth.sms.provider", "unknown")
        ));
        assertThrows(IllegalStateException.class, service::getChannels);
    }

    @Test
    void missingConfigurationIsNotTreatedAsDisabled() {
        when(sysConfigMapper.selectByKeys(any())).thenReturn(List.of());
        assertThrows(IllegalStateException.class, service::getChannels);
    }

    @Test
    void updateDoesNotSucceedWhenAConfigurationRowIsMissing() {
        when(sysConfigMapper.selectLoginSettingsForUpdate()).thenReturn(List.of(
                config("auth.phone.login.enabled", "true"),
                config("auth.email.login.enabled", "true"),
                config("auth.sms.provider", "aliyun")
        ));
        when(sysConfigMapper.updateValue("auth.phone.login.enabled", "false")).thenReturn(1);
        when(sysConfigMapper.updateValue("auth.email.login.enabled", "false")).thenReturn(0);
        assertThrows(IllegalStateException.class,
                () -> service.updateSettings(new AuthChannelUpdateDTO(false, false, "aliyun")));
    }
}
