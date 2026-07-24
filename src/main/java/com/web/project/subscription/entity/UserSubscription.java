package com.web.project.subscription.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户订阅实体。
 *
 * 对应 user_subscription 表。
 * 记录用户对某个商品可以使用到什么时候。
 */
@Getter
@Setter
public class UserSubscription {

    /**
     * 订阅ID。
     */
    private Long id;

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 商品ID。
     */
    private Long productId;

    /**
     * 首次开通时间。
     */
    private LocalDateTime startedAt;

    /**
     * 当前到期时间。
     */
    private LocalDateTime expiresAt;

    /**
     * 状态：0停用，1启用。
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