package com.web.project.subscription.enums;

import lombok.Getter;

/**
 * 客户端使用权限状态。
 *
 * 该状态不是数据库字段，
 * 而是后端根据商品、订阅状态和到期时间计算出的结果。
 */
@Getter
public enum SubscriptionAccessStatus {

    NOT_ACTIVATED("未开通"),

    ACTIVE("有效"),

    EXPIRED("已过期"),

    DISABLED("订阅已停用"),

    PRODUCT_DISABLED("服务已停用");

    private final String description;

    SubscriptionAccessStatus(String description) {
        this.description = description;
    }
}