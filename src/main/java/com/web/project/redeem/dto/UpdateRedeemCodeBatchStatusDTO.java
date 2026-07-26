package com.web.project.redeem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 修改兑换码批次状态的请求参数。
 *
 * @param status 批次状态：0停用，1启用
 */
public record UpdateRedeemCodeBatchStatusDTO(

        @NotNull(message = "批次状态不能为空")
        @Min(value = 0, message = "批次状态不正确")
        @Max(value = 1, message = "批次状态不正确")
        Integer status
) {
}