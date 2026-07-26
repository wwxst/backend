package com.web.project.redeem.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台兑换码分页查询参数。
 */
@Getter
@Setter
public class RedeemCodeQueryDTO {

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
     * 支持搜索脱敏兑换码、兑换用户账号和昵称。
     */
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    /**
     * 兑换码状态：
     * 0未兑换，1已兑换，2已停用。
     *
     * 不传表示查询全部。
     */
    @Min(value = 0, message = "兑换码状态不正确")
    @Max(value = 2, message = "兑换码状态不正确")
    private Integer status;
}