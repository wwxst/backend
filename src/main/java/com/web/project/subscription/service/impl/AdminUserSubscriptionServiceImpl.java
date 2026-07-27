package com.web.project.subscription.service.impl;

import com.web.project.common.result.PageResult;
import com.web.project.product.enums.ProductStatus;
import com.web.project.subscription.dto.UserSubscriptionQueryDTO;
import com.web.project.subscription.enums.SubscriptionAccessStatus;
import com.web.project.subscription.enums.SubscriptionStatus;
import com.web.project.subscription.mapper.UserSubscriptionMapper;
import com.web.project.subscription.mapper.model.UserSubscriptionListRow;
import com.web.project.subscription.service.AdminUserSubscriptionService;
import com.web.project.subscription.vo.UserSubscriptionListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台用户订阅管理业务实现类。
 */
@Service
@RequiredArgsConstructor
public class AdminUserSubscriptionServiceImpl implements AdminUserSubscriptionService {

    private final UserSubscriptionMapper userSubscriptionMapper;

    /**
     * 分页查询用户订阅。
     */
    @Override
    public PageResult<UserSubscriptionListVO> getSubscriptionPage(UserSubscriptionQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();
        String keyword = normalizeNullableText(queryDTO.getKeyword());
        Long userId = queryDTO.getUserId();
        Long productId = queryDTO.getProductId();
        Integer status = queryDTO.getStatus();
        Boolean valid = queryDTO.getValid();
        LocalDateTime currentTime = LocalDateTime.now();

        long total = userSubscriptionMapper.countByCondition(
                keyword,
                userId,
                productId,
                status,
                valid,
                currentTime
        );

        if (total == 0) {
            return new PageResult<>(0L, page, pageSize, List.of());
        }

        long offset = (long) (page - 1) * pageSize;

        List<UserSubscriptionListVO> records = userSubscriptionMapper
                .selectPageByCondition(
                        keyword,
                        userId,
                        productId,
                        status,
                        valid,
                        currentTime,
                        offset,
                        pageSize
                )
                .stream()
                .map(row -> toListVO(row, currentTime))
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }

    /**
     * 将数据库结果转换为接口VO。
     */
    private UserSubscriptionListVO toListVO(UserSubscriptionListRow row, LocalDateTime currentTime) {
        SubscriptionAccessStatus accessStatus = resolveAccessStatus(row, currentTime);
        boolean valid = accessStatus == SubscriptionAccessStatus.ACTIVE;

        long remainingSeconds = valid
                ? Duration.between(currentTime, row.getExpiresAt()).getSeconds()
                : 0;

        return new UserSubscriptionListVO(
                row.getId(),
                row.getUserId(),
                row.getUsername(),
                row.getNickname(),
                row.getUserStatus(),
                row.getProductId(),
                row.getProductCode(),
                row.getProductName(),
                row.getProductStatus(),
                row.getStatus(),
                SubscriptionStatus.descriptionOf(row.getStatus()),
                accessStatus,
                accessStatus.getDescription(),
                valid,
                row.getStartedAt(),
                row.getExpiresAt(),
                remainingSeconds,
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }

    /**
     * 计算用户最终使用权限。
     */
    private SubscriptionAccessStatus resolveAccessStatus(
            UserSubscriptionListRow row,
            LocalDateTime currentTime
    ) {
        if (!Integer.valueOf(1).equals(row.getUserStatus())) {
            return SubscriptionAccessStatus.USER_DISABLED;
        }

        if (!Integer.valueOf(ProductStatus.ENABLED.getCode()).equals(row.getProductStatus())) {
            return SubscriptionAccessStatus.PRODUCT_DISABLED;
        }

        if (!Integer.valueOf(SubscriptionStatus.ENABLED.getCode()).equals(row.getStatus())) {
            return SubscriptionAccessStatus.DISABLED;
        }

        if (row.getExpiresAt() == null || !row.getExpiresAt().isAfter(currentTime)) {
            return SubscriptionAccessStatus.EXPIRED;
        }

        return SubscriptionAccessStatus.ACTIVE;
    }

    /**
     * 清理可为空的查询文本。
     */
    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}