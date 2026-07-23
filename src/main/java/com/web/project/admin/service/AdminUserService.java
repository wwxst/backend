package com.web.project.admin.service;

import com.web.project.admin.dto.AdminUserQueryDTO;
import com.web.project.admin.vo.AdminUserListVO;
import com.web.project.common.result.PageResult;

/**
 * 管理员账号业务接口。
 */
public interface AdminUserService {

    /**
     * 分页查询管理员账号列表。
     *
     * @param queryDTO 查询条件
     * @return 管理员分页列表
     */
    PageResult<AdminUserListVO> getAdminUserPage(AdminUserQueryDTO queryDTO);
}