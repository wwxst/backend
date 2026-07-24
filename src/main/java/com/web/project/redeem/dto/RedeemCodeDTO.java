package com.web.project.redeem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户提交兑换码的参数。
 *
 * @param code 用户输入的兑换码
 */
public record RedeemCodeDTO(

        @NotBlank(message = "兑换码不能为空")
        @Size(max = 64, message = "兑换码长度不能超过64个字符")
        String code
) {
}