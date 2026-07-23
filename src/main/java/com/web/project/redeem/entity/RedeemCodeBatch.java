package com.web.project.redeem.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兑换码批次实体。
 *
 * 对应 redeem_code_batch 表。
 */
@Getter
@Setter
public class RedeemCodeBatch {

    /**
     * 批次主键 ID。
     */
    private Long id;

    /**
     * 批次编号。
     */
    private String batchNo;

    /**
     * 绑定的商品套餐 ID。
     */
    private Long planId;

    /**
     * 本批次生成的兑换码数量。
     */
    private Integer quantity;

    /**
     * 销售渠道或用途说明。
     */
    private String channel;

    /**
     * 兑换码过期时间。
     *
     * 为 null 表示不单独设置过期时间。
     */
    private LocalDateTime expiresAt;

    /**
     * 批次状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 创建该批次的管理员 ID。
     */
    private Long createdBy;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}