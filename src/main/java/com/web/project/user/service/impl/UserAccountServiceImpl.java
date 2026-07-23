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

        /*
         * 去除关键词前后的空格。
         *
         * 如果用户传入的是纯空格，
         * 则按照没有搜索条件处理。
         */
        String keyword = queryDTO.getKeyword();

        if (keyword != null) {
            keyword = keyword.trim();

            if (keyword.isEmpty()) {
                keyword = null;
            }
        }

        Integer status = queryDTO.getStatus();

        // 查询符合条件的数据总数
        long total = userAccountMapper.countByCondition(
                keyword,
                status
        );

        // 没有数据时，不再继续执行分页查询
        if (total == 0) {
            return new PageResult<>(
                    0L,
                    page,
                    pageSize,
                    List.of()
            );
        }

        /*
         * 计算 SQL 分页偏移量。
         *
         * 第 1 页：(1 - 1) × 10 = 0
         * 第 2 页：(2 - 1) × 10 = 10
         */
        long offset = (long) (page - 1) * pageSize;

        List<UserAccount> userAccounts =
                userAccountMapper.selectPageByCondition(
                        keyword,
                        status,
                        offset,
                        pageSize
                );

        /*
         * Entity 转换成 VO。
         *
         * 数据库实体属于内部数据结构，
         * VO 才是接口对外返回的数据结构。
         */
        List<UserAccountListVO> records = userAccounts
                .stream()
                .map(userAccount -> new UserAccountListVO(
                        userAccount.getId(),
                        userAccount.getUsername(),
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