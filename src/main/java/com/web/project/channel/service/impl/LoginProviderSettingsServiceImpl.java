package com.web.project.channel.service.impl;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.entity.SysConfig;
import com.web.project.channel.mapper.SysConfigMapper;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.channel.vo.AliyunSmsSettingsVO;
import com.web.project.channel.vo.EmailLoginSettingsVO;
import com.web.project.channel.vo.TencentSmsSettingsVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.storage.service.CredentialCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 短信和邮件服务商配置。敏感字段使用 AES-GCM 加密后写入 sys_config，
 * 读取接口只返回是否已配置，不回传任何密钥或密码。
 */
@Service
@RequiredArgsConstructor
public class LoginProviderSettingsServiceImpl implements LoginProviderSettingsService {

    private static final String ALIYUN_ACCESS_ID = "auth.sms.aliyun.access-key-id";
    private static final String ALIYUN_ACCESS_SECRET = "auth.sms.aliyun.access-key-secret";
    private static final String ALIYUN_SIGN = "auth.sms.aliyun.sign-name";
    private static final String ALIYUN_TEMPLATE = "auth.sms.aliyun.template-code";

    private static final String TENCENT_SECRET_ID = "auth.sms.tencent.secret-id";
    private static final String TENCENT_SECRET_KEY = "auth.sms.tencent.secret-key";
    private static final String TENCENT_APP_ID = "auth.sms.tencent.sdk-app-id";
    private static final String TENCENT_SIGN = "auth.sms.tencent.sign-name";
    private static final String TENCENT_TEMPLATE = "auth.sms.tencent.template-id";
    private static final String TENCENT_REGION = "auth.sms.tencent.region";

    private static final String EMAIL_HOST = "auth.email.smtp.host";
    private static final String EMAIL_PORT = "auth.email.smtp.port";
    private static final String EMAIL_USERNAME = "auth.email.smtp.username";
    private static final String EMAIL_PASSWORD = "auth.email.smtp.password";
    private static final String EMAIL_FROM = "auth.email.smtp.from-address";
    private static final String EMAIL_FROM_NAME = "auth.email.smtp.from-name";
    private static final String EMAIL_SECURITY = "auth.email.smtp.security";

    private final SysConfigMapper sysConfigMapper;
    private final CredentialCipher cipher;

    @Override
    public AliyunSmsSettingsVO getAliyunSmsSettings() {
        Map<String, String> values = load(List.of(ALIYUN_ACCESS_ID, ALIYUN_ACCESS_SECRET, ALIYUN_SIGN, ALIYUN_TEMPLATE));
        boolean secretConfigured = has(values, ALIYUN_ACCESS_SECRET);
        boolean configured = has(values, ALIYUN_ACCESS_ID) && secretConfigured
                && has(values, ALIYUN_SIGN) && has(values, ALIYUN_TEMPLATE);
        return new AliyunSmsSettingsVO(
                value(values, ALIYUN_ACCESS_ID),
                value(values, ALIYUN_SIGN),
                value(values, ALIYUN_TEMPLATE),
                secretConfigured,
                configured
        );
    }

    @Override
    @Transactional
    public void updateAliyunSmsSettings(AliyunSmsSettingsUpdateDTO dto) {
        Map<String, String> values = loadForUpdate(List.of(ALIYUN_ACCESS_ID, ALIYUN_ACCESS_SECRET, ALIYUN_SIGN, ALIYUN_TEMPLATE));
        String secret = preserveOrEncrypt(dto.accessKeySecret(), value(values, ALIYUN_ACCESS_SECRET), "请填写阿里云 AccessKey Secret");
        write(ALIYUN_ACCESS_ID, dto.accessKeyId().trim());
        write(ALIYUN_ACCESS_SECRET, secret);
        write(ALIYUN_SIGN, dto.signName().trim());
        write(ALIYUN_TEMPLATE, dto.templateCode().trim());
    }

    @Override
    public AliyunSmsConfig requireAliyunSmsConfig() {
        Map<String, String> values = load(List.of(ALIYUN_ACCESS_ID, ALIYUN_ACCESS_SECRET, ALIYUN_SIGN, ALIYUN_TEMPLATE));
        if (!(has(values, ALIYUN_ACCESS_ID) && has(values, ALIYUN_ACCESS_SECRET)
                && has(values, ALIYUN_SIGN) && has(values, ALIYUN_TEMPLATE))) {
            throw notConfigured();
        }
        return new AliyunSmsConfig(
                value(values, ALIYUN_ACCESS_ID),
                cipher.decrypt(value(values, ALIYUN_ACCESS_SECRET)),
                value(values, ALIYUN_SIGN),
                value(values, ALIYUN_TEMPLATE)
        );
    }

    @Override
    public TencentSmsSettingsVO getTencentSmsSettings() {
        Map<String, String> values = load(List.of(
                TENCENT_SECRET_ID, TENCENT_SECRET_KEY, TENCENT_APP_ID,
                TENCENT_SIGN, TENCENT_TEMPLATE, TENCENT_REGION
        ));
        boolean secretConfigured = has(values, TENCENT_SECRET_KEY);
        boolean configured = has(values, TENCENT_SECRET_ID) && secretConfigured
                && has(values, TENCENT_APP_ID) && has(values, TENCENT_SIGN)
                && has(values, TENCENT_TEMPLATE) && has(values, TENCENT_REGION);
        return new TencentSmsSettingsVO(
                value(values, TENCENT_SECRET_ID),
                value(values, TENCENT_APP_ID),
                value(values, TENCENT_SIGN),
                value(values, TENCENT_TEMPLATE),
                value(values, TENCENT_REGION),
                secretConfigured,
                configured
        );
    }

    @Override
    @Transactional
    public void updateTencentSmsSettings(TencentSmsSettingsUpdateDTO dto) {
        Map<String, String> values = loadForUpdate(List.of(
                TENCENT_SECRET_ID, TENCENT_SECRET_KEY, TENCENT_APP_ID,
                TENCENT_SIGN, TENCENT_TEMPLATE, TENCENT_REGION
        ));
        String secret = preserveOrEncrypt(dto.secretKey(), value(values, TENCENT_SECRET_KEY), "请填写腾讯云 SecretKey");
        write(TENCENT_SECRET_ID, dto.secretId().trim());
        write(TENCENT_SECRET_KEY, secret);
        write(TENCENT_APP_ID, dto.sdkAppId().trim());
        write(TENCENT_SIGN, dto.signName().trim());
        write(TENCENT_TEMPLATE, dto.templateId().trim());
        write(TENCENT_REGION, dto.region().trim());
    }

    @Override
    public TencentSmsConfig requireTencentSmsConfig() {
        Map<String, String> values = load(List.of(
                TENCENT_SECRET_ID, TENCENT_SECRET_KEY, TENCENT_APP_ID,
                TENCENT_SIGN, TENCENT_TEMPLATE, TENCENT_REGION
        ));
        if (!(has(values, TENCENT_SECRET_ID) && has(values, TENCENT_SECRET_KEY)
                && has(values, TENCENT_APP_ID) && has(values, TENCENT_SIGN)
                && has(values, TENCENT_TEMPLATE) && has(values, TENCENT_REGION))) {
            throw notConfigured();
        }
        return new TencentSmsConfig(
                value(values, TENCENT_SECRET_ID),
                cipher.decrypt(value(values, TENCENT_SECRET_KEY)),
                value(values, TENCENT_APP_ID),
                value(values, TENCENT_SIGN),
                value(values, TENCENT_TEMPLATE),
                value(values, TENCENT_REGION)
        );
    }

    @Override
    public EmailLoginSettingsVO getEmailSettings() {
        Map<String, String> values = load(List.of(
                EMAIL_HOST, EMAIL_PORT, EMAIL_USERNAME, EMAIL_PASSWORD,
                EMAIL_FROM, EMAIL_FROM_NAME, EMAIL_SECURITY
        ));
        boolean passwordConfigured = has(values, EMAIL_PASSWORD);
        boolean configured = has(values, EMAIL_HOST) && has(values, EMAIL_PORT)
                && has(values, EMAIL_USERNAME) && passwordConfigured
                && has(values, EMAIL_FROM) && has(values, EMAIL_FROM_NAME)
                && has(values, EMAIL_SECURITY);
        return new EmailLoginSettingsVO(
                value(values, EMAIL_HOST),
                parsePort(value(values, EMAIL_PORT)),
                value(values, EMAIL_USERNAME),
                value(values, EMAIL_FROM),
                value(values, EMAIL_FROM_NAME),
                value(values, EMAIL_SECURITY),
                passwordConfigured,
                configured
        );
    }

    @Override
    @Transactional
    public void updateEmailSettings(EmailLoginSettingsUpdateDTO dto) {
        Map<String, String> values = loadForUpdate(List.of(
                EMAIL_HOST, EMAIL_PORT, EMAIL_USERNAME, EMAIL_PASSWORD,
                EMAIL_FROM, EMAIL_FROM_NAME, EMAIL_SECURITY
        ));
        String password = preserveOrEncrypt(dto.password(), value(values, EMAIL_PASSWORD), "请填写 SMTP 密码或授权码");
        write(EMAIL_HOST, dto.host().trim());
        write(EMAIL_PORT, Integer.toString(dto.port()));
        write(EMAIL_USERNAME, dto.username().trim());
        write(EMAIL_PASSWORD, password);
        write(EMAIL_FROM, dto.fromAddress().trim());
        write(EMAIL_FROM_NAME, dto.fromName().trim());
        write(EMAIL_SECURITY, dto.security());
    }

    @Override
    public EmailSmtpConfig requireEmailSmtpConfig() {
        Map<String, String> values = load(List.of(
                EMAIL_HOST, EMAIL_PORT, EMAIL_USERNAME, EMAIL_PASSWORD,
                EMAIL_FROM, EMAIL_FROM_NAME, EMAIL_SECURITY
        ));
        if (!(has(values, EMAIL_HOST) && has(values, EMAIL_PORT)
                && has(values, EMAIL_USERNAME) && has(values, EMAIL_PASSWORD)
                && has(values, EMAIL_FROM) && has(values, EMAIL_FROM_NAME)
                && has(values, EMAIL_SECURITY))) {
            throw notConfigured();
        }
        return new EmailSmtpConfig(
                value(values, EMAIL_HOST),
                parsePort(value(values, EMAIL_PORT)),
                value(values, EMAIL_USERNAME),
                cipher.decrypt(value(values, EMAIL_PASSWORD)),
                value(values, EMAIL_FROM),
                value(values, EMAIL_FROM_NAME),
                value(values, EMAIL_SECURITY)
        );
    }

    private Map<String, String> load(List<String> keys) {
        Map<String, String> values = sysConfigMapper.selectByKeys(keys).stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        if (!values.keySet().containsAll(keys)) {
            throw new IllegalStateException("登录服务商配置缺失，请检查数据库迁移");
        }
        return values;
    }

    private Map<String, String> loadForUpdate(List<String> keys) {
        Map<String, String> values = sysConfigMapper.selectByKeysForUpdate(keys).stream()
                .collect(Collectors.toMap(SysConfig::getConfigKey, SysConfig::getConfigValue));
        if (!values.keySet().containsAll(keys)) {
            throw new IllegalStateException("登录服务商配置缺失，请检查数据库迁移");
        }
        return values;
    }


    private String value(Map<String, String> values, String key) {
        return values.get(key);
    }

    private boolean has(Map<String, String> values, String key) {
        String value = values.get(key);
        return value != null && !value.isBlank();
    }

    private String preserveOrEncrypt(String supplied, String saved, String requiredMessage) {
        String candidate = supplied == null ? "" : supplied.trim();
        String encrypted = candidate.isEmpty() ? saved : cipher.encrypt(candidate);
        if (encrypted == null || encrypted.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, requiredMessage);
        }
        return encrypted;
    }

    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
            // Report malformed persisted configuration below.
        }
        throw new IllegalStateException("SMTP 端口配置缺失或格式错误");
    }

    private void write(String key, String value) {
        if (sysConfigMapper.updateValue(key, value) != 1) {
            throw new IllegalStateException("登录服务商配置未写入预期记录：" + key);
        }
    }

    private BusinessException notConfigured() {
        return new BusinessException(ErrorCode.LOGIN_PROVIDER_NOT_CONFIGURED);
    }
}
