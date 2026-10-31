package com.web.project.auth.code;

import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.Properties;

/** 基于动态 SMTP 配置发送邮箱验证码。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpCodeSender {

    private final LoginProviderSettingsService settingsService;

    static Properties mailProperties(String security) {
        Properties properties = new Properties();
        boolean ssl = "SSL".equals(security);
        boolean starttls = "STARTTLS".equals(security);
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "5000");
        properties.setProperty("mail.smtp.timeout", "5000");
        properties.setProperty("mail.smtp.writetimeout", "5000");
        // JavaMail reads these with Properties.getProperty(); Boolean objects are ignored.
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(starttls));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(starttls));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", Boolean.toString(ssl || starttls));
        return properties;
    }

    public void send(String address, String code) {
        var settings = settingsService.requireEmailSmtpConfig();
        try {
            JavaMailSenderImpl sender = new JavaMailSenderImpl();
            sender.setProtocol("smtp");
            sender.setHost(settings.host());
            sender.setPort(settings.port());
            sender.setUsername(settings.username());
            sender.setPassword(settings.password());
            sender.setDefaultEncoding("UTF-8");

            sender.getJavaMailProperties().putAll(mailProperties(settings.security()));

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(settings.fromAddress(), settings.fromName());
            helper.setTo(address);
            helper.setSubject("Atlas 登录验证码");
            helper.setText("您的验证码是：" + code + "，5 分钟内有效。请勿向他人透露。", false);
            sender.send(message);
        } catch (Exception exception) {
            log.warn("SMTP verification email delivery failed, exception={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.CODE_DELIVERY_FAILED);
        }
    }
}
