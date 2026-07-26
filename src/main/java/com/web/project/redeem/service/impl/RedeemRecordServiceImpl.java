package com.web.project.redeem.service.impl;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import com.web.project.redeem.dto.RedeemRecordQueryDTO;
import com.web.project.redeem.mapper.RedeemRecordMapper;
import com.web.project.redeem.mapper.model.RedeemRecordListRow;
import com.web.project.redeem.service.RedeemRecordService;
import com.web.project.redeem.vo.RedeemRecordListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台兑换记录业务实现类。
 */
@Service
@RequiredArgsConstructor
public class RedeemRecordServiceImpl implements RedeemRecordService {

    private final RedeemRecordMapper redeemRecordMapper;

    /**
     * 分页查询兑换记录。
     */
    @Override
    public PageResult<RedeemRecordListVO> getRecordPage(RedeemRecordQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();
        String keyword = normalizeNullableText(queryDTO.getKeyword());
        Long batchId = queryDTO.getBatchId();
        Long planId = queryDTO.getPlanId();
        Long userId = queryDTO.getUserId();
        LocalDateTime startTime = queryDTO.getStartTime();
        LocalDateTime endTime = queryDTO.getEndTime();

        validateTimeRange(startTime, endTime);

        long total = redeemRecordMapper.countByCondition(
                keyword,
                batchId,
                planId,
                userId,
                startTime,
                endTime
        );

        if (total == 0) {
            return new PageResult<>(0L, page, pageSize, List.of());
        }

        long offset = (long) (page - 1) * pageSize;

        List<RedeemRecordListVO> records = redeemRecordMapper
                .selectPageByCondition(
                        keyword,
                        batchId,
                        planId,
                        userId,
                        startTime,
                        endTime,
                        offset,
                        pageSize
                )
                .stream()
                .map(this::toListVO)
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }

    /**
     * 检查开始时间不能晚于结束时间。
     */
    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    /**
     * 将数据库查询结果转换成接口VO。
     */
    private RedeemRecordListVO toListVO(RedeemRecordListRow row) {
        return new RedeemRecordListVO(
                row.getId(),
                row.getRedeemCodeId(),
                row.getCodeMasked(),
                row.getBatchId(),
                row.getBatchNo(),
                row.getChannel(),
                row.getPlanId(),
                row.getPlanName(),
                row.getDurationDays(),
                row.getProductId(),
                row.getProductCode(),
                row.getProductName(),
                row.getUserId(),
                row.getUsername(),
                row.getNickname(),
                row.getRedeemedAt(),
                row.getRedeemIp()
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