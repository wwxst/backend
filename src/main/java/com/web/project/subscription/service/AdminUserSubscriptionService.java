package com.web.project.subscription.service;


import com.web.project.common.result.PageResult;
import com.web.project.subscription.dto.UserSubscriptionQueryDTO;
import com.web.project.subscription.vo.UserSubscriptionListVO;

/**
 * 后台用户订阅管理业务。
 */
public interface AdminUserSubscriptionService {
    /**
     * 分页查询用户订阅。
     */
    PageResult<UserSubscriptionListVO> getSubscriptionPage(UserSubscriptionQueryDTO queryDTO);

}
