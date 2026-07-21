package com.web.project.admin.mapper;

import com.web.project.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 后台管理员数据库操作接口。
 */
@Mapper
public  interface AdminUserMapper {
    /**
     * 根据登录账号查询管理员。
     *
     * @param username 登录账号
     * @return 查询到的管理员；不存在时返回 null
     */
    AdminUser selectByUsername(@Param("username") String username);

    /**
     * 根据管理员 ID 查询管理员。
     *
     * @param id 管理员 ID
     * @return 管理员信息；不存在时返回 null
     */
    AdminUser selectById(@Param("id") Long id);
}
