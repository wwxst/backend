package com.web.project.admin.controller;

import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.service.SysUserService;
import com.web.project.admin.vo.SysUserListVO;
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
@RequestMapping("/api/sys-user/sys-users")
@RequiredArgsConstructor
public class SysUserController {

    /**
     * 管理员账号业务对象。
     *
     * final 配合 @RequiredArgsConstructor，
     * 由 Spring 通过构造方法完成依赖注入。
     */
    private final SysUserService sysUserService;

    /**
     * 分页查询管理员账号列表。
     *
     * 请求示例：
     * GET /api/sys-user/sys-users?page=1&pageSize=10
     *
     * @param queryDTO 查询条件
     * @return 管理员分页列表
     */
    @GetMapping
    public Result<PageResult<SysUserListVO>> getSysUserPage(@Valid @ModelAttribute SysUserQueryDTO queryDTO) {
        PageResult<SysUserListVO> pageResult = sysUserService.getSysUserPage(queryDTO);
        return Result.success(pageResult);
    }
}
