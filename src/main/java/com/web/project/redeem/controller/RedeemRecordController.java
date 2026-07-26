package com.web.project.redeem.controller;

import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.redeem.dto.RedeemRecordQueryDTO;
import com.web.project.redeem.service.RedeemRecordService;
import com.web.project.redeem.vo.RedeemRecordListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台兑换记录管理接口。
 */
@RestController
@RequestMapping("/api/admin/redeem-records")
@RequiredArgsConstructor
public class RedeemRecordController {

    private final RedeemRecordService redeemRecordService;

    /**
     * 分页查询兑换记录。
     */
    @GetMapping
    public Result<PageResult<RedeemRecordListVO>> getRecordPage(
            @Valid @ModelAttribute RedeemRecordQueryDTO queryDTO
    ) {
        PageResult<RedeemRecordListVO> pageResult = redeemRecordService.getRecordPage(queryDTO);
        return Result.success(pageResult);
    }
}