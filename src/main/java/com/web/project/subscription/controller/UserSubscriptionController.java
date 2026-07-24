package com.web.project.subscription.controller;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import com.web.project.subscription.service.UserSubscriptionService;
import com.web.project.subscription.vo.UserSubscriptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户订阅接口。
 */
@RestController
@RequestMapping("/api/user/subscription")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final UserSubscriptionService userSubscriptionService;

    /**
     * 查询当前用户的自动剪辑使用权限。
     */
    @GetMapping
    public Result<UserSubscriptionVO> getCurrentSubscription(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = parseUserId(jwt);

        UserSubscriptionVO subscriptionVO =
                userSubscriptionService.getCurrentSubscription(userId);

        return Result.success(subscriptionVO);
    }

    /**
     * 从Token的sub字段中获取用户ID。
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