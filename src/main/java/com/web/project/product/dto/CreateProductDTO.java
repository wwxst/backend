package com.web.project.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 创建商品参数。
 *
 * @param productCode 商品编码
 * @param productName 商品名称
 * @param description 商品介绍
 */
public record CreateProductDTO(

        @NotBlank(message = "商品编码不能为空")
        @Size(max = 50, message = "商品编码不能超过50个字符")
        @Pattern(
                regexp = "^[A-Z][A-Z0-9_]*$",
                message = "商品编码只能包含大写字母、数字和下划线，并且必须以字母开头"
        )
        String productCode,

        @NotBlank(message = "商品名称不能为空")
        @Size(max = 100, message = "商品名称不能超过100个字符")
        String productName,

        @Size(max = 1000, message = "商品说明不能超过1000个字符")
        String description
) {
}