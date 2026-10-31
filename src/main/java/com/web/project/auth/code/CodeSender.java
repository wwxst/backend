package com.web.project.auth.code;

import com.web.project.auth.model.TargetType;

/**
 * 验证码发送接口。
 *
 * 由运行时配置的短信或邮件服务商发送。
 */
public interface CodeSender {

    /**
     * 发送验证码。
     *
     * @param targetType 联系方式类型
     * @param target     规范化后的联系方式
     * @param code       6 位数字验证码
     */
    void send(TargetType targetType, String target, String code);
}
