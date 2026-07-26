package com.web.project.redeem.enums;

import lombok.Getter;

/**
 * 兑换码批次状态。
 */
@Getter
public enum RedeemBatchStatus {

    DISABLED(0, "停用"),

    ENABLED(1, "启用");

    private final int code;
    private final String description;

    RedeemBatchStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据数据库状态值获取状态名称。
     */
    public static String descriptionOf(Integer code) {
        if (code == null) {
            return "未知";
        }

        for (RedeemBatchStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }

        return "未知";
    }
}