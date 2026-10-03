package com.web.project.admin.service.impl;

import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.entity.SysUser;
import com.web.project.admin.mapper.SysUserMapper;
import com.web.project.admin.service.SysUserService;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统用户列表业务实现。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;

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

        List<SysUser> sysUsers = sysUserMapper.selectPageByCondition(keyword, status, offset, pageSize);

        // 列表只返回展示字段，密码密文不进入响应。
        List<SysUserListVO> records = sysUsers
                .stream()
                .map(sysUser -> new SysUserListVO(
                        sysUser.getId(),
                        sysUser.getUsername(),
                        sysUser.getNickname(),
                        sysUser.getStatus(),
                        sysUser.getCreatedAt(),
                        sysUser.getUpdatedAt()))
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }
}
