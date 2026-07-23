package com.web.project.user.vo;

import java.time.LocalDateTime;

/**
 * 当前普通用户信息。
 *
 * @param id        用户ID
 * @param username  登录账号
 * @param nickname  用户昵称
 * @param status    用户状态
 * @param createdAt 创建时间
 */
public record UserInfoVO(
        Long id,
        String username,
        String nickname,
        Integer status,
        LocalDateTime createdAt
) {
}