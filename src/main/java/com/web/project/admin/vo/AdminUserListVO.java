package com.web.project.admin.vo;

import java.time.LocalDateTime;

/**
 * 管理员列表返回数据。
 *
 * 不包含 password，避免密码密文被返回给前端。
 *
 * @param id        管理员 ID
 * @param username  登录账号
 * @param nickname  管理员昵称
 * @param status    状态：0禁用，1正常
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record AdminUserListVO(
        Long id,
        String username,
        String nickname,
        Integer status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}