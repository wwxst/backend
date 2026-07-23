package com.web.project.redeem.support;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/**
 * 兑换码生成与处理工具。
 *
 * 主要负责：
 * 1. 生成兑换码；
 * 2. 生成兑换码批次编号；
 * 3. 标准化用户输入的兑换码；
 * 4. 计算兑换码哈希；
 * 5. 生成后台展示用的脱敏兑换码。
 */
@Component
public class RedeemCodeGenerator {

    /**
     * 兑换码品牌前缀。
     *
     * 最终格式：
     * KASI-7M4H9Q2XP8RT6KWD
     */
    private static final String CODE_PREFIX = "KASI";

    /**
     * 随机部分的字符数量。
     */
    private static final int RANDOM_CODE_LENGTH = 16;

    /**
     * 兑换码标准化后的总长度。
     *
     * 标准化后会移除横线，因此格式为：
     * KASI + 16位随机字符
     *
     * 总长度为20位。
     */
    private static final int NORMALIZED_CODE_LENGTH =
            CODE_PREFIX.length() + RANDOM_CODE_LENGTH;

    /**
     * 排除0、O、1、I等容易混淆的字符。
     */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
                    .toCharArray();

    /**
     * 兑换码批次编号中的时间格式。
     */
    private static final DateTimeFormatter BATCH_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 安全随机数生成器。
     *
     * 兑换码属于凭证，不能使用普通的 Random。
     */
    private final SecureRandom secureRandom =
            new SecureRandom();

    /**
     * 生成一个完整兑换码。
     *
     * 示例：
     * KASI-7M4H9Q2XP8RT6KWD
     *
     * @return 完整兑换码
     */
    public String generateCode() {
        StringBuilder randomPart =
                new StringBuilder(RANDOM_CODE_LENGTH);

        for (int i = 0; i < RANDOM_CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(
                    ALPHABET.length
            );

            randomPart.append(ALPHABET[index]);
        }

        /*
         * 品牌前缀后保留一个横线，
         * 16位随机字符中间不再添加横线。
         */
        return CODE_PREFIX + "-" + randomPart;
    }

    /**
     * 生成兑换码批次编号。
     *
     * 示例：
     * RC20260723183000A1B2C3
     *
     * @return 批次编号
     */
    public String generateBatchNo() {
        String timePart =
                LocalDateTime.now()
                        .format(BATCH_TIME_FORMATTER);

        String randomPart =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 6)
                        .toUpperCase(Locale.ROOT);

        return "RC" + timePart + randomPart;
    }

    /**
     * 标准化兑换码。
     *
     * 用户输入时允许存在：
     * 1. 大小写差异；
     * 2. 横线；
     * 3. 空格。
     *
     * 例如：
     * KASI-7M4H9Q2XP8RT6KWD
     *
     * 标准化后：
     * KASI7M4H9Q2XP8RT6KWD
     *
     * @param code 原始兑换码
     * @return 标准化后的兑换码
     */
    public String normalizeCode(String code) {
        if (code == null) {
            return null;
        }

        return code
                .trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "")
                .replaceAll("\\s+", "");
    }

    /**
     * 将兑换码计算为SHA-256哈希。
     *
     * 数据库通过哈希值查找兑换码，
     * 不需要长期保存完整明文。
     *
     * @param code 完整兑换码
     * @return 64位SHA-256十六进制字符串
     */
    public String hashCode(String code) {
        String normalizedCode =
                normalizeAndValidate(code);

        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = messageDigest.digest(
                    normalizedCode.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            /*
             * SHA-256属于Java标准算法。
             *
             * 如果运行环境不支持，
             * 说明是服务器环境异常，不是业务错误，
             * 因此不使用BusinessException。
             */
            throw new IllegalStateException(
                    "当前运行环境不支持SHA-256",
                    exception
            );
        }
    }

    /**
     * 生成后台展示用的脱敏兑换码。
     *
     * 示例：
     * 完整码：KASI-7M4H9Q2XP8RT6KWD
     * 脱敏码：KASI-************6KWD
     *
     * @param code 完整兑换码
     * @return 脱敏后的兑换码
     */
    public String maskCode(String code) {
        String normalizedCode =
                normalizeAndValidate(code);

        String prefix = normalizedCode.substring(
                0,
                CODE_PREFIX.length()
        );

        String lastFour = normalizedCode.substring(
                normalizedCode.length() - 4
        );

        return prefix
                + "-************"
                + lastFour;
    }

    /**
     * 标准化并校验兑换码格式。
     *
     * 当前系统兑换码标准：
     * 1. 必须以KASI开头；
     * 2. 移除横线和空格后必须是20位；
     * 3. 后面包含16位随机字符。
     *
     * @param code 原始兑换码
     * @return 校验通过后的标准化兑换码
     */
    private String normalizeAndValidate(String code) {
        String normalizedCode =
                normalizeCode(code);

        if (normalizedCode == null
                || normalizedCode.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.REDEEM_CODE_INVALID
            );
        }

        if (normalizedCode.length()
                != NORMALIZED_CODE_LENGTH) {
            throw new BusinessException(
                    ErrorCode.REDEEM_CODE_INVALID
            );
        }

        if (!normalizedCode.startsWith(
                CODE_PREFIX
        )) {
            throw new BusinessException(
                    ErrorCode.REDEEM_CODE_INVALID
            );
        }

        return normalizedCode;
    }
}