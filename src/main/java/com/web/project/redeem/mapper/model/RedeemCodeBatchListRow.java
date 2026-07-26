package com.web.project.redeem.mapper.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兑换码批次列表数据库查询结果。
 *
 * 这是Mapper内部使用的查询模型，
 * 不直接作为接口返回结果。
 */
@Getter
@Setter
public class RedeemCodeBatchListRow {

    private Long id;

    private String batchNo;

    private Long planId;

    private String planName;

    private Integer durationDays;

    private Integer quantity;

    private Long unusedQuantity;

    private Long redeemedQuantity;

    private Long disabledQuantity;

    private String channel;

    private Integer status;

    private LocalDateTime expiresAt;

    private Long createdBy;

    private LocalDateTime createdAt;
}