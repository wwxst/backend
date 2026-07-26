package com.web.project.redeem.service;

import com.web.project.common.result.PageResult;
import com.web.project.redeem.dto.CreateRedeemCodeBatchDTO;
import com.web.project.redeem.dto.RedeemCodeBatchQueryDTO;
import com.web.project.redeem.dto.RedeemCodeQueryDTO;
import com.web.project.redeem.dto.UpdateRedeemCodeBatchStatusDTO;
import com.web.project.redeem.vo.RedeemCodeBatchCreateVO;
import com.web.project.redeem.vo.RedeemCodeBatchListVO;
import com.web.project.redeem.vo.RedeemCodeListVO;

/**
 * 兑换码批次业务。
 */
public interface RedeemCodeBatchService {

    /**
     * 创建批次并生成兑换码。
     */
    RedeemCodeBatchCreateVO createBatch(
            Long adminId,
            CreateRedeemCodeBatchDTO createDTO
    );

    /**
     * 分页查询兑换码批次。
     */
    PageResult<RedeemCodeBatchListVO> getBatchPage(RedeemCodeBatchQueryDTO queryDTO);

    /**
     * 修改兑换码批次状态。
     */
    void updateBatchStatus(
            Long batchId,
            UpdateRedeemCodeBatchStatusDTO updateDTO
    );

    /**
     * 分页查询指定批次下的兑换码。
     */
    PageResult<RedeemCodeListVO> getCodePage(
            Long batchId,
            RedeemCodeQueryDTO queryDTO
    );
}

