package com.web.project.product.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 商品实体。
 *
 * 对应数据库中的 product 表。
 * 当前商品代表“自动剪辑脚本后台”这一项服务。
 */
@Getter
@Setter
public class Product {

    /**
     * 商品主键 ID。
     */
    private Long id;

    /**
     * 商品内部编码。
     *
     * 例如：AUTO_EDIT_SYSTEM
     */
    private String productCode;

    /**
     * 商品名称。
     */
    private String productName;

    /**
     * 商品介绍。
     */
    private String description;

    /**
     * 商品状态：
     * 0 表示停用；
     * 1 表示启用。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}