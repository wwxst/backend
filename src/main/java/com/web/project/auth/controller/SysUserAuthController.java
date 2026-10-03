package com.web.project.auth.controller;

import com.web.project.auth.dto.RefreshTokenDTO;
import com.web.project.auth.dto.SysUserLoginDTO;
import com.web.project.auth.service.SysUserAuthService;
import com.web.project.auth.util.JwtClaims;
import com.web.project.auth.vo.AccessTokenVO;
import com.web.project.auth.vo.SysUserInfoVO;
import com.web.project.auth.vo.TokenPairVO;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 系统用户认证接口。
 *
 * 负责接收系统用户登录、刷新、退出和当前身份查询请求。
 */
@RestController
@RequestMapping("/api/sys-user/auth")
@RequiredArgsConstructor
public class SysUserAuthController {

    private final SysUserAuthService sysUserAuthService;

    /**
     * 系统用户登录接口。
     */
    @PostMapping("/login")
    public Result<TokenPairVO> login(@Valid @RequestBody SysUserLoginDTO loginDTO) {
        TokenPairVO tokenPair = sysUserAuthService.login(loginDTO);
        return Result.success(tokenPair);
    }

    /**
     * 使用 Refresh Token 换取新的 Access Token。
     */
    @PostMapping("/refresh")
    public Result<AccessTokenVO> refresh(
            @Valid @RequestBody RefreshTokenDTO refreshTokenDTO
    ) {
        AccessTokenVO accessTokenVO = sysUserAuthService.refresh(refreshTokenDTO);
        return Result.success(accessTokenVO);
    }

    /**
     * 退出登录，删除当前会话。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        sysUserAuthService.logout(JwtClaims.sessionId(jwt));
        return Result.success();
    }

    /**
     * 当前系统用户。
     */
    @GetMapping("/me")
    public Result<SysUserInfoVO> getCurrentSysUser(@AuthenticationPrincipal Jwt jwt) {
        SysUserInfoVO sysUserInfoVO = sysUserAuthService.getCurrentSysUser(JwtClaims.accountId(jwt));

        return Result.success(sysUserInfoVO);
    }
}
