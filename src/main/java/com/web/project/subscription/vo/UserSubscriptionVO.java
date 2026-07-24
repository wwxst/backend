package com.web.project.subscription.vo;

import com.web.project.subscription.enums.SubscriptionAccessStatus;

import java.time.LocalDateTime;

/**
 * 当前用户订阅信息。
 *
 * @param productId              商品ID
 * @param productCode            商品编码
 * @param productName            商品名称
 * @param accessStatus           当前使用权限状态
 * @param accessStatusDescription 状态中文说明
 * @param valid                  当前是否允许使用客户端
 * @param startedAt              首次开通时间
 * @param expiresAt              当前到期时间
 * @param serverTime             后端服务器当前时间
 * @param remainingSeconds       剩余有效秒数
 */
public record UserSubscriptionVO(
        Long productId,
        String productCode,
        String productName,
        SubscriptionAccessStatus accessStatus,
        String accessStatusDescription,
        boolean valid,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime serverTime,
        long remainingSeconds
) {
}