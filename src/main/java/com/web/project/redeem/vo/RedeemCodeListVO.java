package com.web.project.redeem.vo;

import java.time.LocalDateTime;

/**
 * 后台兑换码列表返回数据。
 *
 * @param id                  兑换码ID
 * @param batchId             批次ID
 * @param planId              套餐ID
 * @param codeMasked          脱敏兑换码
 * @param status              状态值
 * @param statusName          状态名称
 * @param expiresAt           过期时间
 * @param redeemedUserId      兑换用户ID
 * @param redeemedUsername    兑换用户登录账号
 * @param redeemedNickname    兑换用户昵称
 * @param redeemedAt          兑换时间
 * @param createdAt           创建时间
 */
public record RedeemCodeListVO(
        Long id,
        Long batchId,
        Long planId,
        String codeMasked,
        Integer status,
        String statusName,
        LocalDateTime expiresAt,
        Long redeemedUserId,
        String redeemedUsername,
        String redeemedNickname,
        LocalDateTime redeemedAt,
        LocalDateTime createdAt
) {
}