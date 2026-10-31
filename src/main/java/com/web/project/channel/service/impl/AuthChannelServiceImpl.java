package com.web.project.channel.service.impl;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 登录基础配置业务实现。直接读取 PostgreSQL，不增加配置缓存。
 */
@Service
@RequiredArgsConstructor
public class AuthChannelServiceImpl implements AuthChannelService {

    private static final String PHONE_LOGIN_ENABLED = "auth.phone.login.enabled";
    private static final String EMAIL_LOGIN_ENABLED = "auth.email.login.enabled";
    private static final String SMS_PROVIDER = "auth.sms.provider";

    private final SysConfigMapper sysConfigMapper;

    @Override
    public AuthChannelConfig getChannels() {
        Map<String, String> values = loadValues(List.of(
                PHONE_LOGIN_ENABLED,
                EMAIL_LOGIN_ENABLED,
                SMS_PROVIDER
        ));
        return new AuthChannelConfig(
                requireBoolean(values, PHONE_LOGIN_ENABLED),
                requireBoolean(values, EMAIL_LOGIN_ENABLED),
                requireProvider(values.get(SMS_PROVIDER))
        );
    }

    @Override
    @Transactional
    public void updateSettings(AuthChannelUpdateDTO dto) {
        Map<String, String> locked = sysConfigMapper.selectLoginSettingsForUpdate()
                .stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));

        // 读取锁定结果，确保配置记录存在且格式正确；两个登录方式允许同时关闭。
        requireBoolean(locked, PHONE_LOGIN_ENABLED);
        requireBoolean(locked, EMAIL_LOGIN_ENABLED);
        requireProvider(locked.get(SMS_PROVIDER));

        updateValue(PHONE_LOGIN_ENABLED, dto.phoneLoginEnabled().toString());
        updateValue(EMAIL_LOGIN_ENABLED, dto.emailLoginEnabled().toString());
        updateValue(SMS_PROVIDER, dto.smsProvider());
    }

    private Map<String, String> loadValues(List<String> keys) {
        return sysConfigMapper.selectByKeys(keys).stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
    }

    private boolean requireBoolean(Map<String, String> values, String key) {
        String value = values.get(key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalStateException("登录配置缺失或格式错误：" + key);
    }

    private String requireProvider(String value) {
        if ("aliyun".equals(value) || "tencent".equals(value)) return value;
        throw new IllegalStateException("短信服务平台配置缺失或格式错误");
    }

    private void updateValue(String key, String value) {
        if (sysConfigMapper.updateValue(key, value) != 1) {
            throw new IllegalStateException("登录配置未写入预期记录：" + key);
        }
    }
}
