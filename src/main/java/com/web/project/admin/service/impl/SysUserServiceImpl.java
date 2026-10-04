package com.web.project.admin.service.impl;

import com.web.project.admin.dto.CreateSysUserDTO;
import com.web.project.admin.dto.ResetSysUserPasswordDTO;
import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.dto.UpdateSysUserDTO;
import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.admin.service.SysUserService;
import com.web.project.admin.service.SysUserAvatarStorage;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.auth.util.PasswordPolicy;
import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 系统用户管理业务实现。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysUserAvatarStorage avatarStorage;

    /**
     * 分页查询系统用户列表。
     */
    @Override
    public PageResult<SysUserListVO> getSysUserPage(SysUserQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();

        // 空白关键词按未提供搜索条件处理。
        String keyword = queryDTO.getKeyword();

        if (keyword != null) {
            keyword = keyword.trim();

            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        Integer status = queryDTO.getStatus();

        long total = sysUserMapper.countByCondition(keyword, status);

        if (total == 0) {
            return new PageResult<>(0L, page, pageSize, List.of());
        }

        long offset = (long) (page - 1) * pageSize;

        List<SysUser> sysUsers = sysUserMapper.selectPageByCondition(
                keyword, status, offset, pageSize, queryDTO.getCreatedAtOrder());

        // 列表只返回展示字段，密码密文不进入响应。
        List<SysUserListVO> records = sysUsers.stream()
                .map(this::toListVO)
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        requireExistingUser(sysUserMapper.updateStatus(id, status));
    }

    @Override
    @Transactional
    public SysUserListVO create(CreateSysUserDTO createDTO) {
        PasswordPolicy.validate(createDTO.password());

        SysUser sysUser = new SysUser();
        sysUser.setUsername(createDTO.username().trim());
        sysUser.setPassword(passwordEncoder.encode(createDTO.password()));
        sysUser.setNickname(createDTO.nickname().trim());
        sysUser.setStatus(createDTO.status());

        try {
            if (sysUserMapper.insert(sysUser) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录账号已存在", exception);
        }

        return toListVO(sysUserMapper.selectById(sysUser.getId()));
    }

    @Override
    @Transactional
    public SysUserListVO update(Long id, UpdateSysUserDTO updateDTO) {
        SysUser sysUser = new SysUser();
        sysUser.setId(id);
        sysUser.setUsername(updateDTO.username().trim());
        sysUser.setNickname(updateDTO.nickname().trim());

        try {
            requireExistingUser(sysUserMapper.update(sysUser));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录账号已存在", exception);
        }

        return toListVO(sysUserMapper.selectById(id));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, ResetSysUserPasswordDTO resetDTO) {
        PasswordPolicy.validate(resetDTO.password());
        int updated = sysUserMapper.updatePassword(id, passwordEncoder.encode(resetDTO.password()));
        requireExistingUser(updated);
    }

    @Override
    @Transactional
    public SysUserListVO updateAvatar(Long id, MultipartFile avatarFile) {
        SysUser currentUser = sysUserMapper.selectById(id);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统用户不存在");
        }

        String avatar = avatarStorage.store(avatarFile);
        try {
            requireExistingUser(sysUserMapper.updateAvatar(id, avatar));
        } catch (RuntimeException exception) {
            avatarStorage.delete(avatar);
            throw exception;
        }

        avatarStorage.delete(currentUser.getAvatar());
        return toListVO(sysUserMapper.selectById(id));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SysUser currentUser = sysUserMapper.selectById(id);
        requireExistingUser(sysUserMapper.deleteById(id));
        avatarStorage.delete(currentUser == null ? null : currentUser.getAvatar());
    }

    private void requireExistingUser(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "系统用户不存在");
        }
    }

    private SysUserListVO toListVO(SysUser sysUser) {
        if (sysUser == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        return new SysUserListVO(
                sysUser.getId(),
                sysUser.getUsername(),
                sysUser.getNickname(),
                sysUser.getAvatar(),
                sysUser.getStatus(),
                sysUser.getCreatedAt(),
                sysUser.getUpdatedAt());
    }
}
