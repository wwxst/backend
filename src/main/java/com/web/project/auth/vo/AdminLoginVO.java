package com.web.project.auth.vo;


/**
 * 管理员登录成功后的返回数据。
 *
 * VO：View Object，
 * 表示专门返回给前端使用的数据对象。
 *
 * @param id       管理员ID
 * @param username 管理员账号
 * @param nickname 管理员昵称
 * @param token    登录凭证
 */
public record AdminLoginVO (
    Long id,
    String username,
    String nickname,
    String token
) {
}