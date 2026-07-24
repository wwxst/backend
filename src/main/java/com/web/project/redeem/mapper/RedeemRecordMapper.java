package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 兑换记录数据库操作。
 */
@Mapper
public interface RedeemRecordMapper {

    /**
     * 保存成功兑换记录。
     */
    int insert(RedeemRecord redeemRecord);
}