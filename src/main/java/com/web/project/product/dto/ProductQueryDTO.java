package com.web.project.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 后台商品分页查询参数。
 *
 * GET 请求中的查询参数会自动绑定到这个对象。
 */
@Getter
@Setter
public class ProductQueryDTO {

    /**
     * 当前页码，默认第 1 页。
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /**
     * 每页数量，默认 10 条，最多 100 条。
     */
    @Min(value = 1, message = "每页数量不能小于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;

    /**
     * 商品编码或商品名称搜索关键词。
     */
    @Size(max = 100, message = "搜索关键词不能超过100个字符")
    private String keyword;

    /**
     * 商品状态：
     * 0 表示停用；
     * 1 表示启用；
     * 不传表示查询全部。
     */
    @Min(value = 0, message = "商品状态不正确")
    @Max(value = 1, message = "商品状态不正确")
    private Integer status;
}