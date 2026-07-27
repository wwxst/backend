package com.web.project.admin.mapper;

import com.web.project.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 后台管理员数据库操作接口。
 */
@Mapper
public  interface AdminUserMapper {
    /**
     * 根据登录账号查询管理员。
     */
    AdminUser selectByUsername(@Param("username") String username);

    /**
     * 新增管理员。
     */
    int insert(AdminUser adminUser);

    /**
     * 根据管理员 ID 查询管理员。
     */
    AdminUser selectById(@Param("id") Long id);

    /**
     * 查询符合条件的管理员总数。
     */
    long countByCondition(
            @Param("keyword") String keyword, //账号或昵称关键词
            @Param("status") Integer status // 管理员状态
    );

    /**
     * 分页查询管理员列表。
     */
    List<AdminUser> selectPageByCondition(
            @Param("keyword") String keyword, //账号或昵称关键词
            @Param("status") Integer status, //管理员状态
            @Param("offset") long offset, //从第几条数据开始查询
            @Param("pageSize") int pageSize //本次查询多少条
    );
}

