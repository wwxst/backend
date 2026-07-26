package com.web.project.redeem.service.impl;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import com.web.project.product.entity.Product;
import com.web.project.product.entity.ProductPlan;
import com.web.project.product.enums.ProductStatus;
import com.web.project.product.mapper.ProductMapper;
import com.web.project.product.mapper.ProductPlanMapper;
import com.web.project.redeem.dto.CreateRedeemCodeBatchDTO;
import com.web.project.redeem.dto.RedeemCodeBatchQueryDTO;
import com.web.project.redeem.entity.RedeemCode;
import com.web.project.redeem.entity.RedeemCodeBatch;
import com.web.project.redeem.enums.RedeemBatchStatus;
import com.web.project.redeem.enums.RedeemCodeStatus;
import com.web.project.redeem.mapper.RedeemCodeBatchMapper;
import com.web.project.redeem.mapper.RedeemCodeMapper;
import com.web.project.redeem.mapper.model.RedeemCodeBatchListRow;
import com.web.project.redeem.service.RedeemCodeBatchService;
import com.web.project.redeem.support.RedeemCodeGenerator;
import com.web.project.redeem.vo.RedeemCodeBatchCreateVO;
import com.web.project.redeem.vo.RedeemCodeBatchListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 兑换码批次业务实现。
 */
@Service
@RequiredArgsConstructor
public class RedeemCodeBatchServiceImpl implements RedeemCodeBatchService {

    private final ProductMapper productMapper;
    private final ProductPlanMapper productPlanMapper;
    private final RedeemCodeBatchMapper batchMapper;
    private final RedeemCodeMapper redeemCodeMapper;
    private final RedeemCodeGenerator codeGenerator;

    /**
     * 创建兑换码批次。
     * <p>
     * 批次、兑换码必须在同一个事务中保存。
     */
    @Override
    @Transactional
    public RedeemCodeBatchCreateVO createBatch(Long adminId, CreateRedeemCodeBatchDTO createDTO) {
        ProductPlan productPlan = productPlanMapper.selectById(createDTO.planId());

        if (productPlan == null) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_NOT_FOUND);
        }

        Product product = productMapper.selectById(productPlan.getProductId());

        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }

        if (!Integer.valueOf(ProductStatus.ENABLED.getCode()).equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_DISABLED);
        }

        if (!Integer.valueOf(ProductStatus.ENABLED.getCode()).equals(productPlan.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_DISABLED);
        }

        if (!Boolean.TRUE.equals(productPlan.getSupportRedeem())) {
            throw new BusinessException(ErrorCode.PRODUCT_PLAN_REDEEM_NOT_SUPPORTED);
        }

        LocalDateTime now = LocalDateTime.now();

        RedeemCodeBatch batch = new RedeemCodeBatch();

        batch.setBatchNo(codeGenerator.generateBatchNo());
        batch.setPlanId(productPlan.getId());
        batch.setQuantity(createDTO.quantity());
        batch.setChannel(normalizeNullableText(createDTO.channel()));
        batch.setExpiresAt(createDTO.expiresAt());
        batch.setStatus(RedeemBatchStatus.ENABLED.getCode());
        batch.setCreatedBy(adminId);
        batch.setCreatedAt(now);

        batchMapper.insert(batch);

        /*
         * LinkedHashSet：
         * 既能去重，又能保留生成顺序。
         */
        Set<String> generatedCodeSet = new LinkedHashSet<>();

        while (generatedCodeSet.size() < createDTO.quantity()) {
            generatedCodeSet.add(codeGenerator.generateCode());
        }

        List<String> plaintextCodes = List.copyOf(generatedCodeSet);

        List<RedeemCode> redeemCodes = new ArrayList<>(plaintextCodes.size());

        for (String plaintextCode : plaintextCodes) {
            RedeemCode redeemCode = new RedeemCode();

            redeemCode.setBatchId(batch.getId());
            redeemCode.setPlanId(productPlan.getId());
            redeemCode.setCodeHash(codeGenerator.hashCode(plaintextCode));
            redeemCode.setCodeMasked(codeGenerator.maskCode(plaintextCode));
            redeemCode.setStatus(RedeemCodeStatus.UNUSED.getCode());
            redeemCode.setExpiresAt(createDTO.expiresAt());

            redeemCodes.add(redeemCode);
        }

        redeemCodeMapper.batchInsert(redeemCodes);

        return new RedeemCodeBatchCreateVO(batch.getId(), batch.getBatchNo(), productPlan.getId(), productPlan.getPlanName(), productPlan.getDurationDays(), batch.getQuantity(), batch.getChannel(), batch.getExpiresAt(), now, plaintextCodes);
    }

    /**
     * 分页查询兑换码批次。
     */
    @Override
    public PageResult<RedeemCodeBatchListVO> getBatchPage(RedeemCodeBatchQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();
        String keyword = normalizeNullableText(queryDTO.getKeyword());
        Long planId = queryDTO.getPlanId();
        Integer status = queryDTO.getStatus();

        long total = batchMapper.countByCondition(keyword, planId, status);

        if (total == 0) {
            return new PageResult<>(0L, page, pageSize, List.of());
        }

        long offset = (long) (page - 1) * pageSize;

        List<RedeemCodeBatchListVO> records = batchMapper
                .selectPageByCondition(keyword, planId, status, offset, pageSize)
                .stream()
                .map(this::toBatchListVO)
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }

    /**
     * 数据库查询结果转换为接口返回VO。
     */
    private RedeemCodeBatchListVO toBatchListVO(
            RedeemCodeBatchListRow row
    ) {
        return new RedeemCodeBatchListVO(
                row.getId(),
                row.getBatchNo(),
                row.getPlanId(),
                row.getPlanName(),
                row.getDurationDays(),
                row.getQuantity(),
                row.getUnusedQuantity(),
                row.getRedeemedQuantity(),
                row.getDisabledQuantity(),
                row.getChannel(),
                row.getStatus(),
                RedeemBatchStatus.descriptionOf(row.getStatus()),
                row.getExpiresAt(),
                row.getCreatedBy(),
                row.getCreatedAt()
        );
    }

    /**
     * 清理可为空的文本。
     */
    private String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();

        return normalized.isEmpty() ? null : normalized;
    }
}