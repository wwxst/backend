package com.web.project.admin.mapper;

import com.web.project.admin.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 系统用户数据库访问接口。
 */
@Mapper
public interface SysUserMapper {
    /**
     * 根据登录账号查询系统用户。
     */
    SysUser selectByUsername(@Param("username") String username);

    /**
     * 根据系统用户 ID 查询账号。
     */
    SysUser selectById(@Param("id") Long id);

    /**
     * 查询符合条件的系统用户总数。
     */
    long countByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status
    );

    /**
     * 分页查询系统用户列表；不读取密码密文。
     *
     * @param keyword 账号或昵称的模糊搜索关键词
     * @param status 账号状态；null 表示不筛选
     * @param offset 从 0 开始的查询偏移量
     * @param pageSize 当前页数量
     */
    List<SysUser> selectPageByCondition(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("pageSize") int pageSize
    );
}
