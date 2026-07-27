package com.web.project.subscription.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台用户订阅分页查询参数。
 */
@Getter
@Setter
public class UserSubscriptionQueryDTO {

    /**
     * 当前页码。
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /**
     * 每页数量。
     */
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;

    /**
     * 搜索关键词。
     *
     * 支持用户账号、昵称、商品编码和商品名称。
     */
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    /**
     * 用户ID。
     */
    @Min(value = 1, message = "用户ID不能小于1")
    private Long userId;

    /**
     * 商品ID。
     */
    @Min(value = 1, message = "商品ID不能小于1")
    private Long productId;

    /**
     * 数据库订阅状态：0停用，1启用。
     */
    @Min(value = 0, message = "订阅状态不正确")
    @Max(value = 1, message = "订阅状态不正确")
    private Integer status;

    /**
     * 最终使用权是否有效。
     *
     * true：当前可以使用客户端。
     * false：过期、停用或账号/商品被停用。
     */
    private Boolean valid;
}