package com.web.project.product.enums;

import lombok.Getter;

/**
 * 商品及套餐状态。
 */
@Getter
public enum ProductStatus {

    DISABLED(0, "停用"),

    ENABLED(1, "启用");

    /**
     * 保存到数据库中的状态值。
     */
    private final int code;

    /**
     * 返回给前端的状态名称。
     */
    private final String description;

    ProductStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态值获取状态名称。
     */
    public static String descriptionOf(Integer code) {
        if (code == null) {
            return "未知";
        }

        for (ProductStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }

        return "未知";
    }
}