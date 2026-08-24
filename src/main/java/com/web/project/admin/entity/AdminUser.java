package com.web.project.admin.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 后台管理员实体类。
 *
 * 实体类中的属性与 sys_user 数据表字段对应。
 */
@Getter
@Setter
public class AdminUser {
    /**
     * 管理员ID。
     */
    private Long id;

    /**
     * 登录账号。
     */
    private String username;

    /**
     * 加密后的登录密码。
     */
    private String password;

    /**
     * 管理员昵称。
     */
    private String nickname;

    /**
     * 账号状态：1正常，0禁用。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
