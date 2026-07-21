package com.web.project.config.security;

import com.web.project.common.error.ErrorCode;
import com.web.project.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证请求处理器。
 *
 * 用于处理：
 * 1. 没有携带 Token
 * 2. Token 格式错误
 * 3. Token 已过期
 * 4. Token 签名验证失败
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    /**
     * Spring Boot 提供的 JSON 转换工具。
     */
    private final ObjectMapper objectMapper;
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        // 设置真正的 HTTP 状态码为 401
        response.setStatus(
                HttpServletResponse.SC_UNAUTHORIZED
        );

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        Result<Void> result = Result.error(
                //登录状态无效或已过期
                ErrorCode.LOGIN_STATUS_INVALID.getCode(),
                ErrorCode.LOGIN_STATUS_INVALID.getDefaultMessage()
        );

        // 将 Result 对象转换成 JSON 写入响应
        objectMapper.writeValue(
                response.getOutputStream(),
                result
        );
    }
}
