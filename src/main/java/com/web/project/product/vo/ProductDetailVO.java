package com.web.project.product.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情。
 *
 * 商品详情中同时返回该商品下的全部套餐。
 */
public record ProductDetailVO(
        Long id,
        String productCode,
        String productName,
        String description,
        Integer status,
        String statusName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ProductPlanVO> plans
) {
}