package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemCodeBatch;
import org.apache.ibatis.annotations.Mapper;

/**
 * 兑换码批次数据库操作。
 */
@Mapper
public interface RedeemCodeBatchMapper {

    /**
     * 创建兑换码批次。
     *
     * 插入成功后会回填批次 ID。
     */
    int insert(RedeemCodeBatch batch);
}