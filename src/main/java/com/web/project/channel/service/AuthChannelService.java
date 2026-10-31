package com.web.project.channel.service;

import com.web.project.channel.dto.AuthChannelUpdateDTO;
import com.web.project.channel.model.AuthChannelConfig;

/**
 * 登录基础配置业务接口。
 */
public interface AuthChannelService {

    AuthChannelConfig getChannels();

    void updateSettings(AuthChannelUpdateDTO dto);
}
