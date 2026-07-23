package com.web.project.auth.vo;

/**
 * 普通用户登录结果。
 *
 * @param token 用户访问令牌
 */
public record UserLoginVO(
        String token
) {
}