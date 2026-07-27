package com.web.project.subscription.vo;

import com.web.project.subscription.enums.SubscriptionAccessStatus;

import java.time.LocalDateTime;

/**
 * 后台用户订阅列表返回数据。
 *
 * @param id                      订阅ID
 * @param userId                  用户ID
 * @param username                登录账号
 * @param nickname                用户昵称
 * @param userStatus              用户账号状态
 * @param productId               商品ID
 * @param productCode             商品编码
 * @param productName             商品名称
 * @param productStatus           商品状态
 * @param status                  订阅数据库状态
 * @param statusName              订阅状态名称
 * @param accessStatus            最终使用权限状态
 * @param accessStatusDescription 最终状态中文说明
 * @param valid                   当前是否允许使用客户端
 * @param startedAt               首次开通时间
 * @param expiresAt               到期时间
 * @param remainingSeconds        剩余秒数
 * @param createdAt               创建时间
 * @param updatedAt               更新时间
 */
public record UserSubscriptionListVO(
        Long id,
        Long userId,
        String username,
        String nickname,
        Integer userStatus,
        Long productId,
        String productCode,
        String productName,
        Integer productStatus,
        Integer status,
        String statusName,
        SubscriptionAccessStatus accessStatus,
        String accessStatusDescription,
        boolean valid,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        long remainingSeconds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}