package com.web.project.auth.service.impl;

import com.web.project.auth.dto.UserLoginDTO;
import com.web.project.auth.service.JwtTokenService;
import com.web.project.auth.service.UserAuthService;
import com.web.project.auth.vo.UserLoginVO;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 普通用户认证业务实现类。
 */
@Service
@RequiredArgsConstructor
public class UserAuthServiceImpl implements UserAuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    /**
     * 普通用户登录。
     */
    @Override
    public UserLoginVO login(UserLoginDTO loginDTO) {

        // UserLoginDTO 是 record，使用 username() 读取属性。
        UserAccount userAccount =
                userAccountMapper.selectByUsername(
                        loginDTO.username()
                );

        /*
         * 账号不存在和密码错误返回相同提示，
         * 避免外部人员通过接口判断账号是否存在。
         */
        if (userAccount == null
                || !passwordEncoder.matches(
                loginDTO.password(),
                userAccount.getPassword()
        )) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        /*
         * 密码验证通过后，再判断用户状态。
         * 1 表示正常，0 表示禁用。
         */
        if (!Integer.valueOf(1).equals(
                userAccount.getStatus()
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DISABLED
            );
        }

        // 生成只拥有 user 权限的 JWT
        String token =
                jwtTokenService.createUserAccessToken(
                        userAccount
                );

        return new UserLoginVO(token);
    }

    /**
     * 获取当前登录用户信息。
     */
    @Override
    public UserInfoVO getCurrentUser(Long userId) {

        /*
         * 不能只相信 Token 中的信息。
         * 需要查询数据库，确认用户仍然存在且未被禁用。
         */
        UserAccount userAccount =
                userAccountMapper.selectById(userId);

        if (userAccount == null) {
            throw new BusinessException(
                    ErrorCode.LOGIN_STATUS_INVALID
            );
        }

        if (!Integer.valueOf(1).equals(
                userAccount.getStatus()
        )) {
            throw new BusinessException(
                    ErrorCode.USER_DISABLED
            );
        }

        return new UserInfoVO(
                userAccount.getId(),
                userAccount.getUsername(),
                userAccount.getNickname(),
                userAccount.getStatus(),
                userAccount.getCreatedAt()
        );
    }
}