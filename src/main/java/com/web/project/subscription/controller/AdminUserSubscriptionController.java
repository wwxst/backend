package com.web.project.subscription.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.subscription.dto.UserSubscriptionQueryDTO;
import com.web.project.subscription.service.AdminUserSubscriptionService;
import com.web.project.subscription.vo.UserSubscriptionListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台用户订阅管理接口。
 */
@RestController
@RequestMapping("/api/admin/user-subscriptions")
@RequiredArgsConstructor
public class AdminUserSubscriptionController {

    private final AdminUserSubscriptionService adminUserSubscriptionService;

    /**
     * 分页查询用户订阅。
     */
    @GetMapping
    public Result<PageResult<UserSubscriptionListVO>> getSubscriptionPage(
            @Valid @ModelAttribute UserSubscriptionQueryDTO queryDTO
    ) {
        PageResult<UserSubscriptionListVO> pageResult =
                adminUserSubscriptionService.getSubscriptionPage(queryDTO);

        return Result.success(pageResult);
    }
}