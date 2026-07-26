package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemCodeBatch;
import com.web.project.redeem.mapper.model.RedeemCodeBatchListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

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

    /**
     * 查询符合条件的批次总数。
     */
    long countByCondition(
            @Param("keyword") String keyword,
            @Param("planId") Long planId,
            @Param("status") Integer status
    );

    /**
     * 分页查询兑换码批次。
     */
    List<RedeemCodeBatchListRow> selectPageByCondition(
            @Param("keyword") String keyword,
            @Param("planId") Long planId,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize
    );
    /**
     * 修改兑换码批次状态。
     */
    int updateStatus(
            @Param("id") Long id,
            @Param("status") Integer status
    );
}