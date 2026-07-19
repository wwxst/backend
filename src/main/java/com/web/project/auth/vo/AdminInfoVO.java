package com.web.project.auth.vo;
/**
 * 当前登录管理员信息。
 *
 * @param id       管理员 ID
 * @param username 管理员账号
 * @param nickname 管理员昵称
 */
public record AdminInfoVO(
        Long id,
        String username,
        String nickname
) {
}
