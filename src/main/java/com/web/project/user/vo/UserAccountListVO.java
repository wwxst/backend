package com.web.project.user.vo;

import java.time.LocalDateTime;

/**
 * 后台用户列表返回数据。
 *
 * 不返回 password 字段。
 *
 * @param id        用户 ID
 * @param username  登录账号
 * @param nickname  用户昵称
 * @param status    状态：0禁用，1正常
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record UserAccountListVO(
        Long id,
        String username,
        String nickname,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}