package com.web.project;


import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JWT 密钥生成测试。
 *
 * 只负责生成随机密钥，不属于正式业务代码。
 */
public class JwtSecretGeneratorTest {
    @Test
    public void generateSecret() {
        /*
         * 创建一个 32 字节的数组。
         * 32 字节 = 256 位，可以用于 HS256。
         */
        byte[] secretBytes = new byte[32];

        // 使用安全随机数填充数组
        new SecureRandom().nextBytes(secretBytes);

        // 转成 Base64 字符串，方便保存到 application.yml
        String secret = Base64.getEncoder()
                .encodeToString(secretBytes);

        System.out.println(secret);
    }
}
