package com.web.project.redeem.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建兑换码批次参数。
 *
 * @param planId    绑定的商品套餐ID
 * @param quantity  生成数量
 * @param channel   销售渠道
 * @param expiresAt 兑换码过期时间
 */
public record CreateRedeemCodeBatchDTO(

        @NotNull(message = "商品套餐不能为空")
        Long planId,

        @NotNull(message = "生成数量不能为空")
        @Min(value = 1, message = "至少生成1个兑换码")
        @Max(value = 1000, message = "单次最多生成1000个兑换码")
        Integer quantity,

        @Size(max = 100, message = "销售渠道不能超过100个字符")
        String channel,

        @Future(message = "兑换码过期时间必须晚于当前时间")
        LocalDateTime expiresAt
) {
}