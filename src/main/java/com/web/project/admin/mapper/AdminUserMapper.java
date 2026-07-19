package com.web.project.admin.mapper;

import com.web.project.admin.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("""
            SELECT id,
                   username,
                   password,
                   nickname,
                   status,
                   created_at,
                   updated_at
            FROM admin_user
            WHERE username = #{username}
            LIMIT 1
            """)
    AdminUser selectByUsername(@Param("username") String username);
    /**
     * 根据管理员 ID 查询管理员。
     *
     * @param id 管理员 ID
     * @return 管理员信息；不存在时返回 null
     */
    @Select("""
        SELECT id,
               username,
               password,
               nickname,
               status,
               created_at,
               updated_at
        FROM admin_user
        WHERE id = #{id}
        LIMIT 1
        """)
    AdminUser selectById(@Param("id") Long id);
}
