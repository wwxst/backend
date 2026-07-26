package com.web.project.redeem.mapper.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兑换记录列表数据库查询结果。
 *
 * 这个类只用于Mapper与Service之间传递查询结果。
 */
@Getter
@Setter
public class RedeemRecordListRow {

    private Long id;

    private Long redeemCodeId;

    private String codeMasked;

    private Long batchId;

    private String batchNo;

    private String channel;

    private Long planId;

    /**
     * 兑换时保存的套餐名称快照。
     */
    private String planName;

    /**
     * 兑换时保存的有效天数快照。
     */
    private Integer durationDays;

    private Long productId;

    private String productCode;

    private String productName;

    private Long userId;

    private String username;

    private String nickname;

    private LocalDateTime redeemedAt;

    private String redeemIp;
}