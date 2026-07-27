package com.web.project.user.mapper;

import com.web.project.user.entity.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 普通用户账号数据库操作接口。
 */
@Mapper
public interface UserAccountMapper {

    /**
     * 根据登录账号查询普通用户。
     */
    UserAccount selectByUsername(@Param("username") String username
    );

    /**
     * 根据用户 ID 查询普通用户。
     */
    UserAccount selectById(@Param("id") Long id
    );

    /**
     * 根据用户ID查询用户并锁定当前行。
     *
     * 只能在事务中调用。
     */
    UserAccount selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询符合条件的用户总数。
     *
     * @param keyword 用户名或昵称关键词
     * @param status  用户状态
     * @return 用户数量
     */
    long countByCondition(
            @Param("keyword") String keyword, //用户名或昵称关键词
            @Param("status") Integer status //用户状态
    );

    /**
     * 分页查询普通用户。
     *
     * @param keyword  用户名或昵称关键词
     * @param status   用户状态
     * @param offset   查询起始位置
     * @param pageSize 每页数量
     * @return 当前页用户数据
     */
    List<UserAccount> selectPageByCondition(
            @Param("keyword") String keyword, //用户名或昵称关键词
            @Param("status") Integer status, //用户状态
            @Param("offset") long offset, //查询起始位置
            @Param("pageSize") int pageSize //当前页用户数据
    );
}