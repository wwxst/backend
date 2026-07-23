package com.web.project.redeem.service;

import com.web.project.redeem.dto.CreateRedeemCodeBatchDTO;
import com.web.project.redeem.vo.RedeemCodeBatchCreateVO;

/**
 * 兑换码批次业务。
 */
public interface RedeemCodeBatchService {

    /**
     * 创建批次并生成兑换码。
     *
     * @param adminId  创建管理员ID
     * @param createDTO 创建参数
     * @return 批次信息和完整兑换码
     */
    RedeemCodeBatchCreateVO createBatch(
            Long adminId,
            CreateRedeemCodeBatchDTO createDTO
    );
}