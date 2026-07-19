package com.web.project.auth.controller;

import com.web.project.auth.dto.LoginDTO;
import com.web.project.auth.service.AdminAuthService;
import com.web.project.auth.vo.AdminInfoVO;
import com.web.project.auth.vo.AdminLoginVO;
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
 * 负责接收管理员登录、退出登录等认证请求。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    /**
     * 管理员登录接口。
     */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        // Controller 不处理具体登录逻辑，
        // 而是将登录参数交给 Service。
        AdminLoginVO loginVo = adminAuthService.login(loginDTO);
        return Result.success(loginVo);
    }

    @GetMapping("/me")
    public Result<AdminInfoVO> getCurrentAdmin(@AuthenticationPrincipal Jwt jwt) {
        /*
         * Token 中的 sub 字段保存的是管理员 ID。
         * jwt.getSubject() 就是读取 sub。
         */
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new BusinessException(401, "登录状态无效");
        }
        Long adminId;
        try {
            // 把字符串形式的管理员 ID 转成 Long
            adminId = Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(401, "登录状态无效");
        }
        AdminInfoVO adminInfoVO =
                adminAuthService.getCurrentAdmin(adminId);

        return Result.success(adminInfoVO);
    }
}
