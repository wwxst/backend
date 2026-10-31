package com.web.project.channel.controller;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.model.AuthChannelConfig;
import com.web.project.channel.service.AuthChannelService;
import com.web.project.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端登录基础配置接口。
 */
@RestController
@RequestMapping("/api/sys-user/login-settings")
@RequiredArgsConstructor
public class AuthChannelController {

    private final AuthChannelService authChannelService;

    @GetMapping
    public Result<AuthChannelConfig> getSettings() {
        return Result.success(authChannelService.getChannels());
    }

    @PutMapping
    public Result<Void> updateSettings(@Valid @RequestBody AuthChannelUpdateDTO updateDTO) {
        authChannelService.updateSettings(updateDTO);
        return Result.success();
    }
}
