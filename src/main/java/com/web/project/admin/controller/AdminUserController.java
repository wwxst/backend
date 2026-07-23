package com.web.project.admin.controller;

import com.web.project.admin.dto.AdminUserQueryDTO;
import com.web.project.admin.service.AdminUserService;
import com.web.project.admin.vo.AdminUserListVO;
import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员账号管理接口。
 */
@RestController
@RequestMapping("/api/admin/admin-users")
@RequiredArgsConstructor
public class AdminUserController {

    /**
     * 管理员账号业务对象。
     *
     * final 配合 @RequiredArgsConstructor，
     * 由 Spring 通过构造方法完成依赖注入。
     */
    private final AdminUserService adminUserService;

    /**
     * 分页查询管理员账号列表。
     *
     * 请求示例：
     * GET /api/admin/admin-users?page=1&pageSize=10
     *
     * @param queryDTO 查询条件
     * @return 管理员分页列表
     */
    @GetMapping
    public Result<PageResult<AdminUserListVO>> getAdminUserPage(@Valid @ModelAttribute AdminUserQueryDTO queryDTO) {
        PageResult<AdminUserListVO> pageResult = adminUserService.getAdminUserPage(queryDTO);
        return Result.success(pageResult);
    }
}