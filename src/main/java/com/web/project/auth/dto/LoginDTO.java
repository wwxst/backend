package com.web.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


/**
 * 登录请求参数。
 *
 * DTO：Data Transfer Object，数据传输对象。
 * 这里用于接收前端提交的账号和密码。
 */

public record LoginDTO(
    /**
     * 登录账号。
     * 登录时不检查密码复杂度，
     * 只限制最大长度，防止提交异常大的字符串。
     */
    @NotBlank(message = "请输入账号")
    @Size(max = 20, message = "账号格式不正确")
    String username,

    /**
     * 登录密码。
     * 登录时不检查密码复杂度，
     * 只限制最大长度，防止提交异常大的字符串。
     */
    @NotBlank(message = "请输入密码")
    @Size(max = 24, message = "密码格式不正确")
    String password
){
}
