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
}