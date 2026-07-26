package com.web.project.redeem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台兑换码批次分页查询参数。
 */
@Getter
@Setter
public class RedeemCodeBatchQueryDTO {

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
     * 支持搜索批次编号、渠道和套餐名称。
     */
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    /**
     * 套餐ID。
     *
     * 不传表示查询所有套餐。
     */
    @Min(value = 1, message = "套餐ID不能小于1")
    private Long planId;

    /**
     * 批次状态：0停用，1启用。
     *
     * 不传表示查询全部。
     */
    @Min(value = 0, message = "批次状态不正确")
    @Max(value = 1, message = "批次状态不正确")
    private Integer status;
}