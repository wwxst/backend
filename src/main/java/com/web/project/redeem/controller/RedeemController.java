package com.web.project.redeem.controller;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import com.web.project.redeem.dto.RedeemCodeDTO;
import com.web.project.redeem.service.RedeemService;
import com.web.project.redeem.vo.RedeemResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户兑换接口。
 */
@RestController
@RequestMapping("/api/user/redemptions")
@RequiredArgsConstructor
public class RedeemController {

    private final RedeemService redeemService;

    /**
     * 用户使用兑换码。
     */
    @PostMapping
    public Result<RedeemResultVO> redeem(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RedeemCodeDTO redeemDTO,
            HttpServletRequest request
    ) {
        Long userId = parseUserId(jwt);
        String redeemIp = request.getRemoteAddr();

        RedeemResultVO result = redeemService.redeem(userId, redeemIp, redeemDTO);
        return Result.success(result);
    }

    /**
     * 从用户Token中获取用户ID。
     */
    private Long parseUserId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }
    }
}