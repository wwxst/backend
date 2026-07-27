package com.web.project.subscription.enums;

import lombok.Getter;

/**
 * 用户订阅状态。
 */
@Getter
public enum SubscriptionStatus {

    DISABLED(0, "停用"),

    ENABLED(1, "启用");

    private final int code;
    private final String description;

    SubscriptionStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据数据库状态值获取中文说明。
     */
    public static String descriptionOf(Integer code) {
        if (code == null) {
            return "未知";
        }

        for (SubscriptionStatus status : values()) {
            if (status.code == code) {
                return status.description;
            }
        }

        return "未知";
    }
}