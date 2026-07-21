package com.web.project.common.exception;

import com.web.project.common.error.ErrorCode;
import lombok.Getter;

/**
 * 自定义业务异常。
 * 用于处理可预期的业务错误，
 * 例如用户不存在、密码错误、账号已禁用等。
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 当前异常对应的错误码定义。
     */
    private final ErrorCode errorCode;

    /**
     * 使用统一错误码创建业务异常。
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        // 暂时使用默认中文提示作为异常消息
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用统一错误码，并自定义本次提示。
     *
     * 仅在提示需要包含动态数据时使用。
     */
    public BusinessException(
            ErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }
}