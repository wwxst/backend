package com.web.project.common.exception;

import lombok.Getter;

/**
 * 自定义业务异常。
 * 用于处理可预期的业务错误，
 * 例如用户不存在、密码错误、账号已禁用等。
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 业务状态码。
     */
    private final int code;

    /**
     * 创建带自定义状态码的业务异常。
     *
     * @param code    业务状态码
     * @param message 错误提示
     */
    public BusinessException(int code, String message) {
        // RuntimeException 负责保存异常提示信息
        super(message);
        this.code = code;
    }

    /**
     * 创建默认状态码为 400 的业务异常。
     *
     * @param message 错误提示
     */
    public BusinessException(String message) {
        this(400, message);
    }
}