package com.web.project.admin.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 系统用户账号，对应 sys_user 表。
 */
@Getter
@Setter
public class SysUser {
    private Long id;

    private String username;

    /**
     * BCrypt 密码哈希，不作为接口返回数据。
     */
    private String password;

    private String nickname;

    /**
     * 账号状态：1正常，0禁用。
     */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
