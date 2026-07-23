package com.web.project.redeem.mapper;

import com.web.project.redeem.entity.RedeemCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}