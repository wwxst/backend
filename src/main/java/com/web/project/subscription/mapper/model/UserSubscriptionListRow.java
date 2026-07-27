package com.web.project.subscription.mapper.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 后台用户订阅列表数据库查询结果。
 *
 * 只在Mapper与Service之间使用。
 */
@Getter
@Setter
public class UserSubscriptionListRow {

    private Long id;

    private Long userId;

    private String username;

    private String nickname;

    private Integer userStatus;

    private Long productId;

    private String productCode;

    private String productName;

    private Integer productStatus;

    /**
     * 订阅数据库状态：0停用，1启用。
     */
    private Integer status;

    private LocalDateTime startedAt;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}