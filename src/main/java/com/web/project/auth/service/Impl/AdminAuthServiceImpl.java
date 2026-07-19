package com.web.project.auth.service.Impl;

import com.web.project.admin.entity.AdminUser;
import com.web.project.admin.mapper.AdminUserMapper;
import com.web.project.auth.dto.LoginDTO;
import com.web.project.auth.service.AdminAuthService;
import com.web.project.auth.service.JwtTokenService;
import com.web.project.auth.vo.AdminInfoVO;
import com.web.project.auth.vo.AdminLoginVO;
import com.web.project.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


/**
 * 管理员认证业务实现类。
 *
 * 负责实现管理员登录、退出登录等具体业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    /**
     * 管理员登录。
     * @param loginDTO 登录参数
     * @return 管理员登录结果
     */
    @Override
    public AdminLoginVO login(LoginDTO loginDTO) {
        // LoginDTO 是 record，读取属性使用 username()，
        // 而不是传统 POJO 的 getUsername()。
        AdminUser adminUser = adminUserMapper.selectByUsername(loginDTO.username());

        /*
         * 账号不存在和密码错误统一返回同一句提示，
         * 避免别人通过接口判断某个账号是否真实存在。
         */
        if (adminUser == null ||
                !passwordEncoder.matches(
                        loginDTO.password(),
                        adminUser.getPassword()
                )) {
            throw new BusinessException(401, "账号或密码错误");
        }

        /*
         * 密码验证成功后，再判断账号是否被禁用。
         * 1 表示正常，0 表示禁用。
         */
        if (!Integer.valueOf(1).equals(adminUser.getStatus())) {
            throw new BusinessException(403, "当前账号已被禁用");
        }

        // 账号验证成功，生成 JWT
        String token = jwtTokenService.createAdminAccessToken(adminUser);

        // 返回管理员信息和 Token
        return new AdminLoginVO(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getNickname(),
                token
        );
    }

    /**
     * 获取当前登录管理员信息。
     *
     * @param adminId 管理员 ID
     * @return 当前管理员信息
     */
    @Override
    public AdminInfoVO getCurrentAdmin(Long adminId) {
        // 根据 Token 中的管理员 ID 查询数据库
        AdminUser adminUser = adminUserMapper.selectById(adminId);
        /*
         * Token 虽然验证成功，但对应账号可能已经被删除。
         * 此时当前登录状态应当视为失效。
         */
        if (adminUser == null) {
            throw new BusinessException(401, "登录状态已失效");
        }

        /*
         * 管理员可能在登录后被后台禁用。
         * 因此不能只相信 Token，还要查询数据库中的最新状态。
         */
        if (!Integer.valueOf(1).equals(adminUser.getStatus())) {
            throw new BusinessException(403, "当前账号已被禁用");
        }

        return new AdminInfoVO(
                adminUser.getId(),
                adminUser.getUsername(),
                adminUser.getNickname()
        );
    }
}
