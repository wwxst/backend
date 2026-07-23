package com.web.project.user.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.user.dto.UserAccountQueryDTO;
import com.web.project.user.service.UserAccountService;
import com.web.project.user.vo.UserAccountListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台普通用户管理接口。
 *
 * 虽然代码属于 user 业务模块，
 * 但接口路径属于管理端，只允许管理员访问。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAccountController {

    private final UserAccountService userAccountService;

    /**
     * 分页查询普通用户列表。
     *
     * 请求示例：
     * GET /api/admin/users?page=1&pageSize=10
     */
    @GetMapping
    public Result<PageResult<UserAccountListVO>> getUserPage(
            @Valid @ModelAttribute UserAccountQueryDTO queryDTO
    ) {
        PageResult<UserAccountListVO> pageResult =
                userAccountService.getUserPage(queryDTO);

        return Result.success(pageResult);
    }
}