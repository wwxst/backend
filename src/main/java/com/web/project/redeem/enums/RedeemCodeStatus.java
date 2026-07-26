package com.web.project.redeem.enums;

import lombok.Getter;

/**
 * 兑换码状态。
 */
@Getter
public enum RedeemCodeStatus {

    /**
     * 兑换码尚未被使用。
     */
    UNUSED(0, "未兑换"),

    /**
     * 兑换码已经被用户兑换。
     */
    REDEEMED(1, "已兑换"),

    /**
     * 兑换码已被管理员停用。
     */
    DISABLED(2, "已停用");

    private final int code;
    private final String description;

    RedeemCodeStatus(int code, String description) {
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

        for (RedeemCodeStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }

        return "未知";
    }
}