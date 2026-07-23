package com.web.project.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建商品套餐参数。
 *
 * @param planCode       套餐编码
 * @param planName       套餐名称
 * @param durationDays    有效天数
 * @param price           套餐价格
 * @param supportRedeem   是否支持兑换码
 * @param supportPayment  是否支持在线支付
 * @param sort            排序值
 */
public record CreateProductPlanDTO(

        @NotBlank(message = "套餐编码不能为空")
        @Size(max = 50, message = "套餐编码不能超过50个字符")
        @Pattern(
                regexp = "^[A-Z][A-Z0-9_]*$",
                message = "套餐编码只能包含大写字母、数字和下划线，并且必须以字母开头"
        )
        String planCode,

        @NotBlank(message = "套餐名称不能为空")
        @Size(max = 100, message = "套餐名称不能超过100个字符")
        String planName,

        @NotNull(message = "套餐有效天数不能为空")
        @Min(value = 1, message = "套餐有效天数不能小于1天")
        @Max(value = 36500, message = "套餐有效天数不能超过36500天")
        Integer durationDays,

        @NotNull(message = "套餐价格不能为空")
        @DecimalMin(
                value = "0.00",
                inclusive = true,
                message = "套餐价格不能小于0"
        )
        @Digits(
                integer = 8,
                fraction = 2,
                message = "套餐价格最多保留两位小数"
        )
        BigDecimal price,

        @NotNull(message = "请选择是否支持兑换码")
        Boolean supportRedeem,

        @NotNull(message = "请选择是否支持在线支付")
        Boolean supportPayment,

        @Min(value = 0, message = "排序值不能小于0")
        Integer sort
) {
}