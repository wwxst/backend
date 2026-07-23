package com.web.project.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 普通用户登录参数。
 *
 * @param username 登录账号
 * @param password 登录密码
 */
public record UserLoginDTO(

        @NotBlank(message = "账号不能为空")
        @Size(max = 50, message = "账号长度不能超过20个字符")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(max = 100, message = "密码长度不能超过48个字符")
        String password
) {
}