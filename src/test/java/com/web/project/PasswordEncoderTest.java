package com.web.project;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密测试。
 */
class PasswordEncoderTest {

    @Test
    void encodePassword() {
        PasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder();

        String encodedPassword =
                passwordEncoder.encode("Admin12345678");

        System.out.println(encodedPassword);
    }
}