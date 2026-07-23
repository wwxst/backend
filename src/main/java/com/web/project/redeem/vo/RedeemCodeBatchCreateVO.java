package com.web.project.redeem.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兑换码批次创建结果。
 *
 * codes 中的完整兑换码只在创建成功时返回一次。
 *
 * @param batchId     批次ID
 * @param batchNo     批次编号
 * @param planId      套餐ID
 * @param planName    套餐名称
 * @param durationDays 套餐有效天数
 * @param quantity    生成数量
 * @param channel     销售渠道
 * @param expiresAt   兑换码过期时间
 * @param createdAt   创建时间
 * @param codes       完整兑换码
 */
public record RedeemCodeBatchCreateVO(
        Long batchId,
        String batchNo,
        Long planId,
        String planName,
        Integer durationDays,
        Integer quantity,
        String channel,
        LocalDateTime expiresAt,
        LocalDateTime createdAt,
        List<String> codes
) {
}