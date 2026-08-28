package com.web.project.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 项目统一错误码。
 * <p>
 * 每一种业务错误都应该有稳定的业务错误码，
 * 不要让前端依赖中文提示判断错误类型。
 */
@Getter
public enum ErrorCode {

    //通用请求错误。
    BAD_REQUEST(40000, HttpStatus.BAD_REQUEST, "请求参数不正确"),

    //登录账号或密码不正确。
    INVALID_CREDENTIALS(40101, HttpStatus.UNAUTHORIZED, "账号或密码错误"),

    //Token 无效或者已过期
    LOGIN_STATUS_INVALID(40102, HttpStatus.UNAUTHORIZED, "登录状态无效或已过期"),

    //当前用户没有目标接口的访问权限。
    ACCESS_DENIED(40301, HttpStatus.FORBIDDEN, "没有权限访问该接口"),

    //管理员账号被禁用。
    ADMIN_DISABLED(40302, HttpStatus.FORBIDDEN, "当前账号已被禁用"),

    //普通用户账号被禁用。
    USER_DISABLED(40303, HttpStatus.FORBIDDEN, "当前用户账号已被禁用"),

    /**
     * 商品错误状态码
     */
    //商品不存在。
    PRODUCT_NOT_FOUND(40401, HttpStatus.NOT_FOUND, "商品不存在"),

    //商品套餐不存在。
    PRODUCT_PLAN_NOT_FOUND(40402, HttpStatus.NOT_FOUND, "商品套餐不存在"),

    //商品编码重复。
    PRODUCT_CODE_EXISTS(40901, HttpStatus.CONFLICT, "商品编码已存在"),

    //套餐编码重复。
    PRODUCT_PLAN_CODE_EXISTS(40902, HttpStatus.CONFLICT, "套餐编码已存在"),

    //商品已停用。
    PRODUCT_DISABLED(40903, HttpStatus.CONFLICT, "商品已停用"),

    //商品套餐已停用。
    PRODUCT_PLAN_DISABLED(40904, HttpStatus.CONFLICT, "商品套餐已停用"),

    //当前套餐不支持兑换码。
    PRODUCT_PLAN_REDEEM_NOT_SUPPORTED(40905, HttpStatus.CONFLICT, "当前套餐不支持兑换码兑换"),

    //兑换码格式不正确。
    REDEEM_CODE_INVALID(40010, HttpStatus.BAD_REQUEST, "兑换码格式不正确"),

    //兑换码不存在。
    REDEEM_CODE_NOT_FOUND(40410, HttpStatus.NOT_FOUND, "兑换码不存在"),

    // 兑换码批次不存在。
    REDEEM_BATCH_NOT_FOUND(40411, HttpStatus.NOT_FOUND, "兑换码批次不存在"),

    //兑换码已经被使用。
    REDEEM_CODE_ALREADY_USED(40910, HttpStatus.CONFLICT, "兑换码已被使用"),

    //兑换码已经过期。
    REDEEM_CODE_EXPIRED(40911, HttpStatus.CONFLICT, "兑换码已过期"),

    //兑换码已被停用。
    REDEEM_CODE_DISABLED(40912, HttpStatus.CONFLICT, "兑换码已被停用"),

    //兑换码所属批次已被停用。
    REDEEM_BATCH_DISABLED(40913, HttpStatus.CONFLICT, "兑换码所属批次已被停用"),


    /**
     * 服务器内部未知异常。
     */
    INTERNAL_SERVER_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部异常");

    /**
     * 返回给前端的业务错误码。
     */
    private final int code;

    /**
     * 对应的 HTTP 状态码。
     */
    private final HttpStatus httpStatus;

    /**
     * 默认中文提示。
     */
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
