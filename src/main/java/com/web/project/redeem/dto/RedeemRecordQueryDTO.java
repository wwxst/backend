package com.web.project.redeem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 后台兑换记录分页查询参数。
 */
@Getter
@Setter
public class RedeemRecordQueryDTO {

    /**
     * 当前页码，默认第1页。
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /**
     * 每页数量，默认10条，最多100条。
     */
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;

    /**
     * 搜索关键词。
     *
     * 支持搜索：
     * 用户账号、用户昵称、脱敏兑换码、
     * 批次编号、套餐名称和商品名称。
     */
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    /**
     * 兑换码批次ID。
     */
    @Min(value = 1, message = "批次ID不能小于1")
    private Long batchId;

    /**
     * 套餐ID。
     */
    @Min(value = 1, message = "套餐ID不能小于1")
    private Long planId;

    /**
     * 用户ID。
     */
    @Min(value = 1, message = "用户ID不能小于1")
    private Long userId;

    /**
     * 兑换时间起点。
     *
     * 示例：
     * 2026-07-01T00:00:00
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    /**
     * 兑换时间终点。
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;
}