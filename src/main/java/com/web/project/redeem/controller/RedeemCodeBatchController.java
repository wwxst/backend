package com.web.project.redeem.controller;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.PageResult;
import com.web.project.common.result.Result;
import com.web.project.redeem.dto.CreateRedeemCodeBatchDTO;
import com.web.project.redeem.dto.RedeemCodeBatchQueryDTO;
import com.web.project.redeem.dto.RedeemCodeQueryDTO;
import com.web.project.redeem.dto.UpdateRedeemCodeBatchStatusDTO;
import com.web.project.redeem.service.RedeemCodeBatchService;
import com.web.project.redeem.vo.RedeemCodeBatchCreateVO;
import com.web.project.redeem.vo.RedeemCodeBatchListVO;
import com.web.project.redeem.vo.RedeemCodeListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * 后台兑换码批次管理接口。
 */
@RestController
@RequestMapping("/api/admin/redeem-code-batches")
@RequiredArgsConstructor
public class RedeemCodeBatchController {

    private final RedeemCodeBatchService batchService;

    /**
     * 创建兑换码批次并生成兑换码。
     */
    @PostMapping
    public Result<RedeemCodeBatchCreateVO> createBatch(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRedeemCodeBatchDTO createDTO) {
        Long adminId = parseAdminId(jwt);

        RedeemCodeBatchCreateVO result = batchService.createBatch(adminId, createDTO);

        return Result.success(result);
    }

    /**
     * 从管理员 Token 中读取管理员 ID。
     */
    private Long parseAdminId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.LOGIN_STATUS_INVALID);
        }
    }

    /**
     * 分页查询兑换码批次。
     */
    @GetMapping
    public Result<PageResult<RedeemCodeBatchListVO>> getBatchPage(@Valid @ModelAttribute RedeemCodeBatchQueryDTO queryDTO) {
        PageResult<RedeemCodeBatchListVO> pageResult = batchService.getBatchPage(queryDTO);

        return Result.success(pageResult);
    }

    /**
     * 启用或停用兑换码批次。
     */
    @PatchMapping("/{batchId}/status")
    public Result<Void> updateBatchStatus(@PathVariable Long batchId, @Valid @RequestBody UpdateRedeemCodeBatchStatusDTO updateDTO) {
        batchService.updateBatchStatus(batchId, updateDTO);
        return Result.success();
    }

    /**
     * 分页查询指定批次下的兑换码。
     */
    @GetMapping("/{batchId}/codes")
    public Result<PageResult<RedeemCodeListVO>> getCodePage(
            @PathVariable Long batchId,
            @Valid @ModelAttribute RedeemCodeQueryDTO queryDTO
    ) {
        PageResult<RedeemCodeListVO> pageResult = batchService.getCodePage(batchId, queryDTO);
        return Result.success(pageResult);
    }
}