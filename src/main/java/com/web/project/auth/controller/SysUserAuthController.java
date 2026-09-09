package com.web.project.auth.controller;

import com.web.project.auth.dto.SysUserLoginDTO;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.auth.vo.SysUserInfoVO;
import com.web.project.auth.vo.SysUserLoginVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 管理员认证接口。
 * <p>
 * 负责接收管理员登录和当前身份查询请求。
 */
@RestController
@RequestMapping("/api/sys-user/auth")
@RequiredArgsConstructor
public class SysUserAuthController {

    private final SysUserAuthService sysUserAuthService;

    /**
     * 管理员登录接口。
     */
    @PostMapping("/login")
    public Result<SysUserLoginVO> login(@Valid @RequestBody SysUserLoginDTO loginDTO) {
        // Controller 不处理具体登录逻辑，
        // 而是将登录参数交给 Service。
        SysUserLoginVO loginVo = sysUserAuthService.login(loginDTO);
        return Result.success(loginVo);
    }

    @GetMapping("/me")
    public Result<SysUserInfoVO> getCurrentSysUser(@AuthenticationPrincipal Jwt jwt) {
        /*
         * Token 中的 sub 字段保存的是管理员 ID。
         * jwt.getSubject() 就是读取 sub。
         */
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID); //登录状态无效或已过期
        }
        Long sysUserId;
        try {
            // 把字符串形式的管理员 ID 转成 Long
            sysUserId = Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);//登录状态无效或已过期
        }
        SysUserInfoVO sysUserInfoVO =
                sysUserAuthService.getCurrentSysUser(sysUserId);

        return Result.success(sysUserInfoVO);
    }
}
