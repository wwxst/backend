package com.web.project.admin.service;

import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.common.result.PageResult;

/**
 * 管理员账号业务接口。
 */
public interface SysUserService {

    /**
     * 分页查询管理员账号列表。
     *
     * @param queryDTO 查询条件
     * @return 管理员分页列表
     */
    PageResult<SysUserListVO> getSysUserPage(SysUserQueryDTO queryDTO);
}
