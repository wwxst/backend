package com.web.project.subscription.service.impl;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.product.entity.Product;
import com.web.project.product.enums.ProductStatus;
import com.web.project.product.mapper.ProductMapper;
import com.web.project.subscription.entity.UserSubscription;
import com.web.project.subscription.enums.SubscriptionAccessStatus;
import com.web.project.subscription.enums.SubscriptionStatus;
import com.web.project.subscription.mapper.UserSubscriptionMapper;
import com.web.project.subscription.service.UserSubscriptionService;
import com.web.project.subscription.vo.UserSubscriptionVO;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 用户订阅业务实现类。
 */
@Service
@RequiredArgsConstructor
public class UserSubscriptionServiceImpl implements UserSubscriptionService {

    /**
     * 自动剪辑商品编码。
     *
     * 不直接写死商品ID，
     * 避免不同数据库环境下商品ID发生变化。
     */
    private static final String AUTO_EDIT_PRODUCT_CODE = "AUTO_EDIT_SYSTEM";

    private final UserAccountMapper userAccountMapper;
    private final ProductMapper productMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;

    /**
     * 查询当前用户的自动剪辑使用权限。
     */
    @Override
    public UserSubscriptionVO getCurrentSubscription(Long userId) {
        validateUser(userId);

        LocalDateTime serverTime = LocalDateTime.now();

        /*
         * 根据固定商品编码查询商品。
         * 商品ID由数据库决定，因此不能直接假设商品ID一定是1。
         */
        Product product = productMapper.selectByCode(AUTO_EDIT_PRODUCT_CODE);

        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        UserSubscription subscription =
                userSubscriptionMapper.selectByUserIdAndProductId(userId, product.getId());

        /*
         * 商品被全局停用时，即使用户订阅没有到期，
         * 客户端也不能继续使用。
         */
        if (!Integer.valueOf(ProductStatus.ENABLED.getCode()).equals(product.getStatus())) {
            return buildResult(
                    product,
                    subscription,
                    SubscriptionAccessStatus.PRODUCT_DISABLED,
                    false,
                    serverTime,
                    0
            );
        }

        /*
         * 没有订阅记录，代表用户从未开通过。
         */
        if (subscription == null) {
            return buildResult(
                    product,
                    null,
                    SubscriptionAccessStatus.NOT_ACTIVATED,
                    false,
                    serverTime,
                    0
            );
        }

        /*
         * 订阅被后台主动停用。
         */
        if (!Integer.valueOf(SubscriptionStatus.ENABLED.getCode()).equals(subscription.getStatus())) {
            return buildResult(
                    product,
                    subscription,
                    SubscriptionAccessStatus.DISABLED,
                    false,
                    serverTime,
                    0
            );
        }

        /*
         * 到期时间为空，或者到期时间没有晚于服务器当前时间，
         * 都按照已经过期处理。
         */
        if (subscription.getExpiresAt() == null ||
                !subscription.getExpiresAt().isAfter(serverTime)) {
            return buildResult(
                    product,
                    subscription,
                    SubscriptionAccessStatus.EXPIRED,
                    false,
                    serverTime,
                    0
            );
        }

        long remainingSeconds =
                Duration.between(serverTime, subscription.getExpiresAt()).getSeconds();

        return buildResult(
                product,
                subscription,
                SubscriptionAccessStatus.ACTIVE,
                true,
                serverTime,
                remainingSeconds
        );
    }

    /**
     * 检查Token对应的用户是否仍然存在并且状态正常。
     */
    private void validateUser(Long userId) {
        UserAccount userAccount = userAccountMapper.selectById(userId);

        if (userAccount == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        if (!Integer.valueOf(1).equals(userAccount.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DISABLED);
        }
    }

    /**
     * 统一构建返回结果。
     */
    private UserSubscriptionVO buildResult(
            Product product,
            UserSubscription subscription,
            SubscriptionAccessStatus accessStatus,
            boolean valid,
            LocalDateTime serverTime,
            long remainingSeconds
    ) {
        LocalDateTime startedAt =
                subscription == null ? null : subscription.getStartedAt();

        LocalDateTime expiresAt =
                subscription == null ? null : subscription.getExpiresAt();

        return new UserSubscriptionVO(
                product.getId(),
                product.getProductCode(),
                product.getProductName(),
                accessStatus,
                accessStatus.getDescription(),
                valid,
                startedAt,
                expiresAt,
                serverTime,
                remainingSeconds
        );
    }
}