package com.web.project.product.entity;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品套餐实体。
 *
 * 对应数据库中的 product_plan 表。
 *
 * 一个商品可以包含多个套餐，例如：
 * 30天标准版、90天专业版、365天年卡。
 */
@Getter
@Setter
public class ProductPlan {

    /**
     * 套餐主键 ID。
     */
    private Long id;

    /**
     * 所属商品 ID。
     */
    private Long productId;

    /**
     * 套餐内部编码。
     *
     * 例如：AUTO_EDIT_30D
     */
    private String planCode;

    /**
     * 套餐名称。
     */
    private String planName;

    /**
     * 套餐有效天数。
     */
    private Integer durationDays;

    /**
     * 套餐销售价格。
     *
     * 金额使用 BigDecimal，避免浮点数精度问题。
     */
    private BigDecimal price;

    /**
     * 是否支持兑换码：
     * false 不支持；
     * true 支持。
     */
    private Boolean supportRedeem;

    /**
     * 套餐状态：
     * 0 表示停用；
     * 1 表示启用。
     */
    private Integer status;

    /**
     * 排序值。
     *
     * 数值越小，列表位置越靠前。
     */
    private Integer sort;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
