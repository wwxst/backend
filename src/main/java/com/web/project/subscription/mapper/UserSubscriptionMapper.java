package com.web.project.subscription.mapper;

import com.web.project.subscription.entity.UserSubscription;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 用户订阅数据库操作。
 */
@Mapper
public interface UserSubscriptionMapper {

    /**
     * 查询用户对某个商品的订阅并加行锁。
     *
     * 该方法必须在事务中使用。
     */
    UserSubscription selectForUpdate(
            @Param("userId") Long userId,
            @Param("productId") Long productId
    );

    /**
     * 查询用户对某个商品的订阅。
     *
     * 这是普通查询，不加数据库行锁，
     * 用于客户端检查当前使用权限。
     */
    UserSubscription selectByUserIdAndProductId(
            @Param("userId") Long userId,
            @Param("productId") Long productId
    );

    /**
     * 新增用户订阅。
     */
    int insert(UserSubscription subscription);

    /**
     * 修改订阅到期时间和状态。
     */
    int updateExpiration(
            @Param("id") Long id,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("status") Integer status
    );
}