package com.web.project.user.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 普通用户账号实体。
 *
 * 对应数据库中的 user_account 表。
 */
@Getter
@Setter
public class UserAccount {

    /**
     * 用户主键 ID。
     */
    private Long id;

    /**
     * 登录账号。
     */
    private String username;

    /**
     * BCrypt 加密后的密码。
     */
    private String password;

    /**
     * 用户昵称。
     */
    private String nickname;

    /**
     * 用户状态：
     * 0 表示禁用，1 表示正常。
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