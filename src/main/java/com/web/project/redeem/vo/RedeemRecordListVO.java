package com.web.project.redeem.vo;

import java.time.LocalDateTime;

/**
 * 后台兑换记录列表返回数据。
 *
 * @param id           兑换记录ID
 * @param redeemCodeId 兑换码ID
 * @param codeMasked   脱敏兑换码
 * @param batchId      批次ID
 * @param batchNo      批次编号
 * @param channel      销售渠道
 * @param planId       套餐ID
 * @param planName     兑换时的套餐名称
 * @param durationDays 本次增加的有效天数
 * @param productId    商品ID
 * @param productCode  商品编码
 * @param productName  商品名称
 * @param userId       用户ID
 * @param username     用户登录账号
 * @param nickname     用户昵称
 * @param redeemedAt   兑换时间
 * @param redeemIp     兑换IP
 */
public record RedeemRecordListVO(
        Long id,
        Long redeemCodeId,
        String codeMasked,
        Long batchId,
        String batchNo,
        String channel,
        Long planId,
        String planName,
        Integer durationDays,
        Long productId,
        String productCode,
        String productName,
        Long userId,
        String username,
        String nickname,
        LocalDateTime redeemedAt,
        String redeemIp
) {
}