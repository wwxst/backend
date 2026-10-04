package com.web.project.user.service.impl;

import com.web.project.common.result.PageResult;
import com.web.project.user.dto.UserAccountQueryDTO;
import com.web.project.user.entity.UserAccount;
import com.web.project.user.mapper.UserAccountMapper;
import com.web.project.user.service.UserAccountService;
import com.web.project.user.vo.UserAccountListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 普通用户管理业务实现类。
 */
@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {

    private final UserAccountMapper userAccountMapper;

    /**
     * 分页查询普通用户列表。
     */
    @Override
    public PageResult<UserAccountListVO> getUserPage(
            UserAccountQueryDTO queryDTO
    ) {
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

        long total = userAccountMapper.countByCondition(
                keyword,
                status
        );

        if (total == 0) {
            return new PageResult<>(
                    0L,
                    page,
                    pageSize,
                    List.of()
            );
        }

        long offset = (long) (page - 1) * pageSize;

        List<UserAccount> userAccounts =
                userAccountMapper.selectPageByCondition(
                        keyword,
                        status,
                        offset,
                        pageSize,
                        queryDTO.getCreatedAtOrder()
                );

        // 列表只返回展示字段，密码与密码版本不进入响应。
        List<UserAccountListVO> records = userAccounts
                .stream()
                .map(userAccount -> new UserAccountListVO(
                        userAccount.getId(),
                        userAccount.getUserNo(),
                        userAccount.getPhone(),
                        userAccount.getEmail(),
                        userAccount.getNickname(),
                        userAccount.getStatus(),
                        userAccount.getCreatedAt(),
                        userAccount.getUpdatedAt()
                ))
                .toList();

        return new PageResult<>(
                total,
                page,
                pageSize,
                records
        );
    }
}
