package com.web.project.redeem.service;

import com.web.project.common.result.PageResult;
import com.web.project.redeem.dto.RedeemRecordQueryDTO;
import com.web.project.redeem.vo.RedeemRecordListVO;

/**
 * 后台兑换记录业务。
 */
public interface RedeemRecordService {

    /**
     * 分页查询兑换记录。
     */
    PageResult<RedeemRecordListVO> getRecordPage(RedeemRecordQueryDTO queryDTO);
}