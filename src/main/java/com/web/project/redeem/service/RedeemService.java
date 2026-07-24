package com.web.project.redeem.service;

import com.web.project.redeem.dto.RedeemCodeDTO;
import com.web.project.redeem.vo.RedeemResultVO;

/**
 * 用户兑换业务接口。
 */
public interface RedeemService {

    /**
     * 用户使用兑换码开通或延长商品使用期限。
     *
     * @param userId    当前登录用户ID
     * @param redeemIp  用户兑换时的IP
     * @param redeemDTO 用户提交的兑换码
     * @return 兑换结果
     */
    RedeemResultVO redeem(Long userId, String redeemIp, RedeemCodeDTO redeemDTO);
}