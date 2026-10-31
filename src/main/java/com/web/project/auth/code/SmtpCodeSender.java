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

            Properties properties = sender.getJavaMailProperties();
            properties.put("mail.smtp.auth", "true");
            properties.put("mail.smtp.connectiontimeout", "5000");
            properties.put("mail.smtp.timeout", "5000");
            properties.put("mail.smtp.writetimeout", "5000");
            properties.put("mail.smtp.ssl.enable", "SSL".equals(settings.security()));
            properties.put("mail.smtp.starttls.enable", "STARTTLS".equals(settings.security()));
            properties.put("mail.smtp.starttls.required", "STARTTLS".equals(settings.security()));

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
