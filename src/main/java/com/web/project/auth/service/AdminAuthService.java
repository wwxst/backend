package com.web.project.auth.service;

import com.web.project.auth.dto.LoginDTO;
import com.web.project.auth.vo.AdminInfoVO;
import com.web.project.auth.vo.AdminLoginVO;

/**
 * 管理员认证业务接口。
 *
 * 这里只定义认证业务需要提供的方法，
 * 具体登录逻辑由实现类 AdminAuthServiceImpl 完成。
 */
public interface AdminAuthService {
    /**
     * 管理员登录。
     *
     * @param loginDTO 前端提交的登录参数
     * @return 登录成功后的管理员信息和 Token
     */
    AdminLoginVO login(LoginDTO loginDTO);

    /**
     * 获取当前登录管理员信息。
     *
     * @param adminId Token 中解析出的管理员 ID
     * @return 当前管理员信息
     */
    AdminInfoVO getCurrentAdmin(Long adminId);
}
