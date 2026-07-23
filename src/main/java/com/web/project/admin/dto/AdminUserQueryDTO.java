package com.web.project.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 管理员列表查询参数。
 *
 * GET 请求中的查询参数会被 Spring
 * 自动绑定到这个 DTO 中。
 */
@Getter
@Setter
public class AdminUserQueryDTO {

    /**
     * 当前页码，默认从第 1 页开始。
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
     * 搜索关键词。
     *
     * 后面用于模糊搜索账号和昵称。
     */
    @Size(max = 30, message = "搜索关键词不能超过30个字符")
    private String keyword;

    /**
     * 管理员状态：
     * 0 表示禁用，1 表示正常。
     *
     * 不传时查询全部状态。
     */
    @Min(value = 0, message = "管理员状态不正确")
    @Max(value = 1, message = "管理员状态不正确")
    private Integer status;
}