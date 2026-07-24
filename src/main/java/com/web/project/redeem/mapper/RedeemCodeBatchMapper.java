package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemCodeBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 兑换码批次数据库操作。
 */
@Mapper
public interface RedeemCodeBatchMapper {

    /**
     * 创建兑换码批次。
     */
    int insert(RedeemCodeBatch batch);

    /**
     * 根据批次ID查询兑换码批次。
     */
    RedeemCodeBatch selectById(@Param("id") Long id);
}