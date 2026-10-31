package com.web.project.auth.code;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SmtpCodeSenderTest {

    @Test
    void sslModeUsesStringPropertiesAndChecksServerIdentity() {
        Properties properties = SmtpCodeSender.mailProperties("SSL");

        assertEquals("true", properties.getProperty("mail.smtp.ssl.enable"));
        assertEquals("false", properties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("false", properties.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
    }

    @Test
    void startTlsModeRequiresUpgradeAndChecksServerIdentity() {
        Properties properties = SmtpCodeSender.mailProperties("STARTTLS");

        assertEquals("false", properties.getProperty("mail.smtp.ssl.enable"));
        assertEquals("true", properties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", properties.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
    }

    @Test
    void noneModeDoesNotEnableTls() {
        Properties properties = SmtpCodeSender.mailProperties("NONE");

        assertEquals("false", properties.getProperty("mail.smtp.ssl.enable"));
        assertEquals("false", properties.getProperty("mail.smtp.starttls.enable"));
        assertEquals("false", properties.getProperty("mail.smtp.starttls.required"));
        assertEquals("false", properties.getProperty("mail.smtp.ssl.checkserveridentity"));
    }
}
