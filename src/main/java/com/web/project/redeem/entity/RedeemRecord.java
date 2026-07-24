package com.web.project.redeem.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兑换记录实体。
 *
 * 对应 redeem_record 表。
 */
@Getter
@Setter
public class RedeemRecord {

    /**
     * 兑换记录ID。
     */
    private Long id;

    /**
     * 被使用的兑换码ID。
     */
    private Long redeemCodeId;

    /**
     * 兑换码批次ID。
     */
    private Long batchId;

    /**
     * 套餐ID。
     */
    private Long planId;

    /**
     * 兑换用户ID。
     */
    private Long userId;

    /**
     * 兑换时的套餐名称快照。
     */
    private String planName;

    /**
     * 兑换时增加的天数快照。
     */
    private Integer durationDays;

    /**
     * 兑换时间。
     */
    private LocalDateTime redeemedAt;

    /**
     * 兑换请求IP。
     */
    private String redeemIp;
}