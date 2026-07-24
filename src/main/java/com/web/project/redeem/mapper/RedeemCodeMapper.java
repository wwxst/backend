package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 兑换码数据库操作。
 */
@Mapper
public interface RedeemCodeMapper {

    /**
     * 批量保存兑换码。
     */
    int batchInsert(@Param("codes") List<RedeemCode> codes);

    /**
     * 根据兑换码哈希查询兑换码。
     *
     * @param codeHash SHA-256哈希
     * @return 兑换码，不存在时返回null
     */
    RedeemCode selectByCodeHash(@Param("codeHash") String codeHash);

    /**
     * 原子地将兑换码标记为已兑换。
     *
     * SQL中会附带 status = 未兑换条件，
     * 防止同一个兑换码被并发使用两次。
     *
     * @return 更新成功返回1，否则返回0
     */
    int markAsRedeemed(
            @Param("id") Long id,
            @Param("unusedStatus") Integer unusedStatus,
            @Param("redeemedStatus") Integer redeemedStatus,
            @Param("userId") Long userId,
            @Param("redeemedAt") LocalDateTime redeemedAt
    );
}