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
 * 管理员账号业务实现类。
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    /**
     * 分页查询管理员账号列表。
     */
    @Override
    public PageResult<SysUserListVO> getSysUserPage(SysUserQueryDTO queryDTO) {
        int page = queryDTO.getPage();
        int pageSize = queryDTO.getPageSize();

        /*
         * 清理搜索关键词前后的空格。
         *
         * 如果前端传递的是纯空格，
         * 就按照没有关键词处理。
         */
        String keyword = queryDTO.getKeyword();

        if (keyword != null) {
            keyword = keyword.trim();

            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        Integer status = queryDTO.getStatus();

        // 先查询符合条件的数据总数
        long total = sysUserMapper.countByCondition(keyword, status);

        /*
         * 没有数据时直接返回空列表，
         * 避免继续执行分页查询 SQL。
         */
        if (total == 0) {
            return new PageResult<>(0L, page, pageSize, List.of());
        }

        /*
         * 计算分页偏移量。
         *
         * 第 1 页：(1 - 1) × 10 = 0
         * 第 2 页：(2 - 1) × 10 = 10
         */
        long offset = (long) (page - 1) * pageSize;

        // 查询当前页的数据
        List<SysUser> sysUsers = sysUserMapper.selectPageByCondition(keyword, status, offset, pageSize);

        /*
         * Entity 转换成 VO。
         *
         * 不直接返回 SysUser，
         * 避免把 password 字段返回给前端。
         */
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
