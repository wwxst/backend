package com.web.project.subscription.service;

import com.web.project.subscription.vo.UserSubscriptionVO;

/**
 * 用户订阅业务接口。
 */
public interface UserSubscriptionService {

    /**
     * 查询当前用户对自动剪辑商品的使用权限。
     *
     * @param userId 当前登录用户ID
     * @return 订阅和使用权限信息
     */
    UserSubscriptionVO getCurrentSubscription(Long userId);
}