package com.web.project.admin.service;

import com.web.project.admin.dto.SysUserQueryDTO;
import com.web.project.admin.vo.SysUserListVO;
import com.web.project.common.result.PageResult;

/**
 * 系统用户列表业务接口。
 */
public interface SysUserService {

    /**
     * 分页查询系统用户列表。
     *
     * @param queryDTO 查询条件
     * @return 系统用户分页列表
     */
    PageResult<SysUserListVO> getSysUserPage(SysUserQueryDTO queryDTO);
}
