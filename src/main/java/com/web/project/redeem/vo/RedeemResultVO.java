package com.web.project.redeem.vo;

import java.time.LocalDateTime;

/**
 * 兑换成功后的返回数据。
 *
 * @param productId   商品ID
 * @param productName 商品名称
 * @param planId      套餐ID
 * @param planName    套餐名称
 * @param addedDays   本次增加的天数
 * @param redeemedAt  兑换时间
 * @param expiresAt   新的到期时间
 */
public record RedeemResultVO(
        Long productId,
        String productName,
        Long planId,
        String planName,
        Integer addedDays,
        LocalDateTime redeemedAt,
        LocalDateTime expiresAt
) {
}