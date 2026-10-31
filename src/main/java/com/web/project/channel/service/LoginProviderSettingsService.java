package com.web.project.channel.service;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.vo.AliyunSmsSettingsVO;
import com.web.project.channel.vo.EmailLoginSettingsVO;
import com.web.project.channel.vo.TencentSmsSettingsVO;

/** 管理短信与邮件服务商配置，并为验证码发送器提供解密后的运行配置。 */
public interface LoginProviderSettingsService {

    AliyunSmsSettingsVO getAliyunSmsSettings();
    void updateAliyunSmsSettings(AliyunSmsSettingsUpdateDTO dto);
    AliyunSmsConfig requireAliyunSmsConfig();

    TencentSmsSettingsVO getTencentSmsSettings();
    void updateTencentSmsSettings(TencentSmsSettingsUpdateDTO dto);
    TencentSmsConfig requireTencentSmsConfig();

    EmailLoginSettingsVO getEmailSettings();
    void updateEmailSettings(EmailLoginSettingsUpdateDTO dto);
    EmailSmtpConfig requireEmailSmtpConfig();

    record AliyunSmsConfig(String accessKeyId, String accessKeySecret, String signName, String templateCode) {
        @Override public String toString() { return "AliyunSmsConfig[credentials=redacted, signName=" + signName + "]"; }
    }

    record TencentSmsConfig(String secretId, String secretKey, String sdkAppId, String signName, String templateId, String region) {
        @Override public String toString() { return "TencentSmsConfig[credentials=redacted, sdkAppId=" + sdkAppId + "]"; }
    }

    record EmailSmtpConfig(String host, int port, String username, String password, String fromAddress, String fromName, String security) {
        @Override public String toString() { return "EmailSmtpConfig[host=" + host + ", credentials=redacted]"; }
    }
}
