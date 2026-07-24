package com.web.project.redeem.service.impl;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.product.entity.Product;
import com.web.project.product.entity.ProductPlan;
import com.web.project.product.enums.ProductStatus;
import com.web.project.product.mapper.ProductMapper;
import com.web.project.product.mapper.ProductPlanMapper;
import com.web.project.redeem.dto.RedeemCodeDTO;
import com.web.project.redeem.entity.RedeemCode;
import com.web.project.redeem.entity.RedeemCodeBatch;
import com.web.project.redeem.entity.RedeemRecord;
import com.web.project.redeem.enums.RedeemBatchStatus;
import com.web.project.redeem.enums.RedeemCodeStatus;
import com.web.project.redeem.mapper.RedeemCodeBatchMapper;
import com.web.project.redeem.mapper.RedeemCodeMapper;
import com.web.project.redeem.mapper.RedeemRecordMapper;
import com.web.project.redeem.service.RedeemService;
import com.web.project.redeem.support.RedeemCodeGenerator;
import com.web.project.redeem.vo.RedeemResultVO;
import com.web.project.subscription.entity.UserSubscription;
import com.web.project.subscription.enums.SubscriptionStatus;
import com.web.project.subscription.mapper.UserSubscriptionMapper;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户兑换业务实现类。
 */
@Service
@RequiredArgsConstructor
public class RedeemServiceImpl implements RedeemService {

    private final UserAccountMapper userAccountMapper;
    private final RedeemCodeGenerator redeemCodeGenerator;
    private final RedeemCodeMapper redeemCodeMapper;
    private final RedeemCodeBatchMapper redeemCodeBatchMapper;
    private final RedeemRecordMapper redeemRecordMapper;
    private final ProductPlanMapper productPlanMapper;
    private final ProductMapper productMapper;
    private final UserSubscriptionMapper userSubscriptionMapper;

    /**
     * 使用兑换码开通或延长使用期限。
     *
     * 兑换码状态、兑换记录和用户订阅必须一起成功。
     * 任意一步出现业务异常，整个事务都会回滚。
     */
    @Override
    @Transactional
    public RedeemResultVO redeem(Long userId, String redeemIp, RedeemCodeDTO redeemDTO) {
        validateUser(userId);

        LocalDateTime now = LocalDateTime.now();
        String codeHash = redeemCodeGenerator.hashCode(redeemDTO.code());

        RedeemCode redeemCode = redeemCodeMapper.selectByCodeHash(codeHash);
        validateRedeemCode(redeemCode, now);

        RedeemCodeBatch batch = redeemCodeBatchMapper.selectById(redeemCode.getBatchId());
        validateBatch(batch, now);

        ProductPlan productPlan = productPlanMapper.selectById(redeemCode.getPlanId());
        validateProductPlan(productPlan);

        Product product = productMapper.selectById(productPlan.getProductId());
        validateProduct(product);

        /*
         * 查询并锁定当前用户对该商品的订阅。
         *
         * 同一用户同时提交多个兑换码时，
         * 后续请求需要等待当前事务完成，避免订阅天数互相覆盖。
         */
        UserSubscription subscription = userSubscriptionMapper.selectForUpdate(userId, product.getId());

        /*
         * 原子修改兑换码状态。
         *
         * SQL带有 status = 未兑换条件，
         * 因此同一个兑换码并发提交时只能有一个请求成功。
         */
        int affectedRows = redeemCodeMapper.markAsRedeemed(
                redeemCode.getId(),
                RedeemCodeStatus.UNUSED.getCode(),
                RedeemCodeStatus.REDEEMED.getCode(),
                userId,
                now
        );

        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_ALREADY_USED);
        }

        LocalDateTime newExpiresAt = grantSubscription(
                subscription,
                userId,
                product.getId(),
                productPlan.getDurationDays(),
                now
        );

        RedeemRecord redeemRecord = new RedeemRecord();
        redeemRecord.setRedeemCodeId(redeemCode.getId());
        redeemRecord.setBatchId(redeemCode.getBatchId());
        redeemRecord.setPlanId(productPlan.getId());
        redeemRecord.setUserId(userId);
        redeemRecord.setPlanName(productPlan.getPlanName());
        redeemRecord.setDurationDays(productPlan.getDurationDays());
        redeemRecord.setRedeemedAt(now);
        redeemRecord.setRedeemIp(normalizeIp(redeemIp));

        redeemRecordMapper.insert(redeemRecord);

        return new RedeemResultVO(
                product.getId(),
                product.getProductName(),
                productPlan.getId(),
                productPlan.getPlanName(),
                productPlan.getDurationDays(),
                now,
                newExpiresAt
        );
    }

    /**
     * 检查当前用户是否仍然存在并且状态正常。
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
     * 检查兑换码状态和有效期。
     */
    private void validateRedeemCode(RedeemCode redeemCode, LocalDateTime now) {
        if (redeemCode == null) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_NOT_FOUND);
        }

        if (Integer.valueOf(RedeemCodeStatus.REDEEMED.getCode()).equals(redeemCode.getStatus())) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_ALREADY_USED);
        }

        if (Integer.valueOf(RedeemCodeStatus.DISABLED.getCode()).equals(redeemCode.getStatus())) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_DISABLED);
        }

        if (!Integer.valueOf(RedeemCodeStatus.UNUSED.getCode()).equals(redeemCode.getStatus())) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_INVALID);
        }

        if (isExpired(redeemCode.getExpiresAt(), now)) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_EXPIRED);
        }
    }

    /**
     * 检查兑换码所属批次。
     */
    private void validateBatch(RedeemCodeBatch batch, LocalDateTime now) {
        /*
         * 批次不存在通常代表数据已经不完整。
         * 对用户统一显示为兑换码不存在，不暴露内部数据关系。
         */
        if (batch == null) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_NOT_FOUND);
        }

        if (!Integer.valueOf(RedeemBatchStatus.ENABLED.getCode()).equals(batch.getStatus())) {
            throw new BusinessException(ErrorCode.REDEEM_BATCH_DISABLED);
        }

        if (isExpired(batch.getExpiresAt(), now)) {
            throw new BusinessException(ErrorCode.REDEEM_CODE_EXPIRED);
        }
    }

    /**
     * 检查商品套餐是否可以兑换。
     */
    private void validateProductPlan(ProductPlan productPlan) {
        if (productPlan == null) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_NOT_FOUND);
        }

        if (!Integer.valueOf(ProductStatus.ENABLED.getCode()).equals(productPlan.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_DISABLED);
        }

        if (!Boolean.TRUE.equals(productPlan.getSupportRedeem())) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_REDEEM_NOT_SUPPORTED);
        }

        if (productPlan.getDurationDays() == null || productPlan.getDurationDays() <= 0) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_REDEEM_NOT_SUPPORTED);
        }
    }

    /**
     * 检查商品是否启用。
     */
    private void validateProduct(Product product) {
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (!Integer.valueOf(ProductStatus.ENABLED.getCode()).equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_DISABLED);
        }
    }

    /**
     * 创建或延长用户订阅。
     */
    private LocalDateTime grantSubscription(
            UserSubscription subscription,
            Long userId,
            Long productId,
            Integer durationDays,
            LocalDateTime now
    ) {
        if (subscription == null) {
            LocalDateTime expiresAt = now.plusDays(durationDays);

            UserSubscription newSubscription = new UserSubscription();
            newSubscription.setUserId(userId);
            newSubscription.setProductId(productId);
            newSubscription.setStartedAt(now);
            newSubscription.setExpiresAt(expiresAt);
            newSubscription.setStatus(SubscriptionStatus.ENABLED.getCode());

            userSubscriptionMapper.insert(newSubscription);
            return expiresAt;
        }

        LocalDateTime currentExpiresAt = subscription.getExpiresAt();

        /*
         * 未过期：从原到期时间继续增加天数。
         * 已过期：从当前时间重新计算。
         */
        LocalDateTime baseTime = currentExpiresAt != null && currentExpiresAt.isAfter(now)
                ? currentExpiresAt
                : now;

        LocalDateTime newExpiresAt = baseTime.plusDays(durationDays);

        userSubscriptionMapper.updateExpiration(
                subscription.getId(),
                newExpiresAt,
                SubscriptionStatus.ENABLED.getCode()
        );

        return newExpiresAt;
    }

    /**
     * 判断指定时间是否已经过期。
     */
    private boolean isExpired(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * 清理IP地址，避免超过数据库VARCHAR(45)限制。
     */
    private String normalizeIp(String redeemIp) {
        if (redeemIp == null) {
            return null;
        }

        String normalized = redeemIp.trim();

        if (normalized.isEmpty()) {
            return null;
        }

        return normalized.length() <= 45 ? normalized : normalized.substring(0, 45);
    }
}