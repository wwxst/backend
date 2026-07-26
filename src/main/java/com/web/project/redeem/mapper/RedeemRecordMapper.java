package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemRecord;
import com.web.project.redeem.mapper.model.RedeemRecordListRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兑换记录数据库操作。
 */
@Mapper
public interface RedeemRecordMapper {

    /**
     * 保存成功兑换记录。
     */
    int insert(RedeemRecord redeemRecord);

    /**
     * 查询符合条件的兑换记录总数。
     */
    long countByCondition(
            @Param("keyword") String keyword,
            @Param("batchId") Long batchId,
            @Param("planId") Long planId,
            @Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * 分页查询兑换记录。
     */
    List<RedeemRecordListRow> selectPageByCondition(
            @Param("keyword") String keyword,
            @Param("batchId") Long batchId,
            @Param("planId") Long planId,
            @Param("userId") Long userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize
    );
}