package com.web.project.subscription.enums;

import lombok.Getter;

/**
 * 用户最终使用权限状态。
 *
 * 该状态不是数据库字段，
 * 而是根据用户、商品、订阅状态和到期时间动态计算。
 */
@Getter
public enum SubscriptionAccessStatus {

    NOT_ACTIVATED("未开通"),

    ACTIVE("有效"),

    EXPIRED("已过期"),

    DISABLED("订阅已停用"),

    USER_DISABLED("账号已停用"),

    PRODUCT_DISABLED("服务已停用");

    private final String description;

    SubscriptionAccessStatus(String description) {
        this.description = description;
    }
}