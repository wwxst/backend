package com.web.project.redeem.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兑换码实体。
 *
 * 对应 redeem_code 表。
 */
@Getter
@Setter
public class RedeemCode {

    private Long id;

    /**
     * 所属兑换码批次 ID。
     */
    private Long batchId;

    /**
     * 绑定的商品套餐 ID。
     */
    private Long planId;

    /**
     * 完整兑换码经过 SHA-256 计算后的哈希值。
     *
     * 数据库不保存完整明文兑换码。
     */
    private String codeHash;

    /**
     * 后台列表展示的脱敏兑换码。
     *
     * 例如：KASI-****-****-****-8H2K
     */
    private String codeMasked;

    /**
     * 状态：0未兑换，1已兑换，2已停用。
     */
    private Integer status;

    /**
     * 兑换码过期时间。
     */
    private LocalDateTime expiresAt;

    /**
     * 兑换该码的用户 ID。
     */
    private Long redeemedUserId;

    /**
     * 兑换时间。
     */
    private LocalDateTime redeemedAt;

    private LocalDateTime createdAt;
}