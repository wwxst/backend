package com.web.project.auth.controller;

import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.service.UserAuthService;
import com.web.project.auth.vo.UserLoginVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import com.web.project.user.vo.UserInfoVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户认证接口。
 */
@RestController
@RequestMapping("/api/user/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserAuthService userAuthService;

    /**
     * 普通用户登录。
     */
    @PostMapping("/login")
    public Result<UserLoginVO> login(
            @Valid @RequestBody UserLoginDTO loginDTO
    ) {
        UserLoginVO loginVO =
                userAuthService.login(loginDTO);

        return Result.success(loginVO);
    }

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/me")
    public Result<UserInfoVO> getCurrentUser(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = parseUserId(jwt);

        UserInfoVO userInfoVO =
                userAuthService.getCurrentUser(userId);

        return Result.success(userInfoVO);
    }

    /**
     * 从 JWT 的 sub 字段中读取用户 ID。
     */
    private Long parseUserId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null) {
            throw new BusinessException(
                    ErrorCode.LOGIN_STATUS_INVALID
            );
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    ErrorCode.LOGIN_STATUS_INVALID
            );
        }
    }
}