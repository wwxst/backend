package com.web.project.redeem.mapper.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 兑换码列表数据库查询结果。
 *
 * 仅供Mapper和Service内部使用。
 */
@Getter
@Setter
public class RedeemCodeListRow {

    private Long id;

    private Long batchId;

    private Long planId;

    private String codeMasked;

    private Integer status;

    private LocalDateTime expiresAt;

    private Long redeemedUserId;

    private String redeemedUsername;

    private String redeemedNickname;

    private LocalDateTime redeemedAt;

    private LocalDateTime createdAt;
}