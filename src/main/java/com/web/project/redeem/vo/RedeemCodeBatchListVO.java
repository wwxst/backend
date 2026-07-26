package com.web.project.redeem.vo;

import java.time.LocalDateTime;

/**
 * 后台兑换码批次列表返回数据。
 *
 * @param id               批次ID
 * @param batchNo          批次编号
 * @param planId           套餐ID
 * @param planName         套餐名称
 * @param durationDays     套餐有效天数
 * @param quantity         本批次生成总数
 * @param unusedQuantity   未兑换数量
 * @param redeemedQuantity 已兑换数量
 * @param disabledQuantity 已停用数量
 * @param channel          销售渠道
 * @param status           批次状态
 * @param statusName       批次状态名称
 * @param expiresAt        过期时间
 * @param createdBy        创建管理员ID
 * @param createdAt        创建时间
 */
public record RedeemCodeBatchListVO(
        Long id,
        String batchNo,
        Long planId,
        String planName,
        Integer durationDays,
        Integer quantity,
        Long unusedQuantity,
        Long redeemedQuantity,
        Long disabledQuantity,
        String channel,
        Integer status,
        String statusName,
        LocalDateTime expiresAt,
        Long createdBy,
        LocalDateTime createdAt
) {
}