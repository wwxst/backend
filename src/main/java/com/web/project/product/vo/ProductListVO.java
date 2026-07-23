package com.web.project.product.vo;

import java.time.LocalDateTime;

/**
 * 后台商品列表返回数据。
 *
 * @param id          商品ID
 * @param productCode 商品编码
 * @param productName 商品名称
 * @param description 商品说明
 * @param status      商品状态
 * @param statusName  商品状态名称
 * @param createdAt   创建时间
 * @param updatedAt   更新时间
 */
public record ProductListVO(
        Long id,
        String productCode,
        String productName,
        String description,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}