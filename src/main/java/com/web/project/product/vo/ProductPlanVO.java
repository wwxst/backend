package com.web.project.product.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品套餐返回数据。
 *
 * @param id               套餐ID
 * @param productId        商品ID
 * @param planCode         套餐编码
 * @param planName         套餐名称
 * @param durationDays     有效天数
 * @param price            套餐价格
 * @param supportRedeem    是否支持兑换码
 * @param status           套餐状态
 * @param statusName       套餐状态名称
 * @param sort             排序值
 * @param createdAt        创建时间
 * @param updatedAt        更新时间
 */
public record ProductPlanVO(
        Long id,
        Long productId,
        String planCode,
        String planName,
        Integer durationDays,
        BigDecimal price,
        Boolean supportRedeem,
        Integer status,
        String statusName,
        Integer sort,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
