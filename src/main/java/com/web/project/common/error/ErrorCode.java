package com.web.project.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 项目统一错误码。
 *
 * 每一种业务错误都应该有稳定的业务错误码，
 * 不要让前端依赖中文提示判断错误类型。
 */
@Getter
public enum ErrorCode {

    /**
     * 通用请求错误。
     */
    BAD_REQUEST(
            40000,
            HttpStatus.BAD_REQUEST,
            "common.bad_request",
            "请求参数不正确"
    ),

    /**
     * 登录账号或密码不正确。
     */
    INVALID_CREDENTIALS(
            40101,
            HttpStatus.UNAUTHORIZED,
            "auth.invalid_credentials",
            "账号或密码错误"
    ),

    /**
     * Token 无效或者已过期。
     */
    LOGIN_STATUS_INVALID(
            40102,
            HttpStatus.UNAUTHORIZED,
            "auth.login_status_invalid",
            "登录状态无效或已过期"
    ),

    /**
     * 当前用户没有目标接口的访问权限。
     */
    ACCESS_DENIED(
            40301,
            HttpStatus.FORBIDDEN,
            "auth.access_denied",
            "没有权限访问该接口"
    ),

    /**
     * 管理员账号被禁用。
     */
    ADMIN_DISABLED(
            40302,
            HttpStatus.FORBIDDEN,
            "admin.disabled",
            "当前账号已被禁用"
    ),

    /**
     * 服务器内部未知异常。
     */
    INTERNAL_SERVER_ERROR(
            50000,
            HttpStatus.INTERNAL_SERVER_ERROR,
            "common.internal_server_error",
            "服务器内部异常"
    );

    /**
     * 返回给前端的业务错误码。
     */
    private final int code;

    /**
     * 对应的 HTTP 状态码。
     */
    private final HttpStatus httpStatus;

    /**
     * 多语言消息键。
     */
    private final String messageKey;

    /**
     * 默认中文提示。
     */
    private final String defaultMessage;

    ErrorCode(
            int code,
            HttpStatus httpStatus,
            String messageKey,
            String defaultMessage
    ) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }
}