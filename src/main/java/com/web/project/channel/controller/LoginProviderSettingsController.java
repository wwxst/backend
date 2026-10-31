package com.web.project.channel.controller;

import com.web.project.channel.dto.AliyunSmsSettingsUpdateDTO;
import com.web.project.channel.dto.EmailLoginSettingsUpdateDTO;
import com.web.project.channel.dto.TencentSmsSettingsUpdateDTO;
import com.web.project.channel.service.LoginProviderSettingsService;
import com.web.project.channel.vo.AliyunSmsSettingsVO;
import com.web.project.channel.vo.EmailLoginSettingsVO;
import com.web.project.channel.vo.TencentSmsSettingsVO;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端短信服务商和邮箱 SMTP 配置接口。 */
@RestController
@RequestMapping("/api/sys-user/login-providers")
@RequiredArgsConstructor
public class LoginProviderSettingsController {

    private final LoginProviderSettingsService settingsService;

    @GetMapping("/sms/aliyun")
    public Result<AliyunSmsSettingsVO> getAliyunSmsSettings() {
        return Result.success(settingsService.getAliyunSmsSettings());
    }

    @PutMapping("/sms/aliyun")
    public Result<AliyunSmsSettingsVO> updateAliyunSmsSettings(
            @Valid @RequestBody AliyunSmsSettingsUpdateDTO dto
    ) {
        settingsService.updateAliyunSmsSettings(dto);
        return Result.success(settingsService.getAliyunSmsSettings());
    }

    @GetMapping("/sms/tencent")
    public Result<TencentSmsSettingsVO> getTencentSmsSettings() {
        return Result.success(settingsService.getTencentSmsSettings());
    }

    @PutMapping("/sms/tencent")
    public Result<TencentSmsSettingsVO> updateTencentSmsSettings(
            @Valid @RequestBody TencentSmsSettingsUpdateDTO dto
    ) {
        settingsService.updateTencentSmsSettings(dto);
        return Result.success(settingsService.getTencentSmsSettings());
    }

    @GetMapping("/email")
    public Result<EmailLoginSettingsVO> getEmailSettings() {
        return Result.success(settingsService.getEmailSettings());
    }

    @PutMapping("/email")
    public Result<EmailLoginSettingsVO> updateEmailSettings(
            @Valid @RequestBody EmailLoginSettingsUpdateDTO dto
    ) {
        settingsService.updateEmailSettings(dto);
        return Result.success(settingsService.getEmailSettings());
    }
}
