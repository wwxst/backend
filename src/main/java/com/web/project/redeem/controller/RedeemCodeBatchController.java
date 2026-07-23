package com.web.project.redeem.controller;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.exception.BusinessException;
import com.web.project.common.result.Result;
import com.web.project.redeem.dto.CreateRedeemCodeBatchDTO;
import com.web.project.redeem.service.RedeemCodeBatchService;
import com.web.project.redeem.vo.RedeemCodeBatchCreateVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Result<RedeemCodeBatchCreateVO> createBatch(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody
            CreateRedeemCodeBatchDTO createDTO
    ) {
        Long adminId = parseAdminId(jwt);

        RedeemCodeBatchCreateVO result =
                batchService.createBatch(
                        adminId,
                        createDTO
                );

        return Result.success(result);
    }

    /**
     * 从管理员 Token 中读取管理员 ID。
     */
    private Long parseAdminId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null) {
            throw new BusinessException(
                    ErrorCode.LOGIN_STATUS_INVALID
            );
        }

        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    ErrorCode.LOGIN_STATUS_INVALID
            );
        }
    }
}