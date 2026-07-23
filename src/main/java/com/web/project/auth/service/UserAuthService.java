package com.web.project.auth.service;

import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.vo.UserLoginVO;
import com.web.project.user.vo.UserInfoVO;

/**
 * 普通用户认证业务接口。
 */
public interface UserAuthService {

    /**
     * 普通用户登录。
     *
     * @param loginDTO 登录参数
     * @return 登录结果
     */
    UserLoginVO login(UserLoginDTO loginDTO);

    /**
     * 获取当前登录用户信息。
     *
     * @param userId Token 中的用户 ID
     * @return 当前用户信息
     */
    UserInfoVO getCurrentUser(Long userId);
}