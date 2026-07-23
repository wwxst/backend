package com.web.project.admin.service.impl;

import com.web.project.admin.dto.AdminUserQueryDTO;
import com.web.project.admin.entity.AdminUser;
import com.web.project.admin.mapper.AdminUserMapper;
import com.web.project.admin.service.AdminUserService;
import com.web.project.admin.vo.AdminUserListVO;
import com.web.project.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理员账号业务实现类。
 */
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final AdminUserMapper adminUserMapper;
    /**
     * 分页查询管理员账号列表。
     */
    @Override
    public PageResult<AdminUserListVO> getAdminUserPage(AdminUserQueryDTO queryDTO) {
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
        long total = adminUserMapper.countByCondition(keyword, status);

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
        List<AdminUser> adminUsers = adminUserMapper.selectPageByCondition(keyword, status, offset, pageSize);

        /*
         * Entity 转换成 VO。
         *
         * 不直接返回 AdminUser，
         * 避免把 password 字段返回给前端。
         */
        List<AdminUserListVO> records = adminUsers
                .stream()
                .map(adminUser -> new AdminUserListVO(
                        adminUser.getId(),
                        adminUser.getUsername(),
                        adminUser.getNickname(),
                        adminUser.getStatus(),
                        adminUser.getCreatedAt(),
                        adminUser.getUpdatedAt()))
                .toList();

        return new PageResult<>(total, page, pageSize, records);
    }
}