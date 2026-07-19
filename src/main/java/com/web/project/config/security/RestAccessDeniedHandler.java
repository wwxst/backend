package com.web.project.config.security;

import com.web.project.common.result.Result;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 权限不足处理器。
 *
 * 当前用户已经通过身份认证，
 * 但没有权限访问目标接口时执行。
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    /**
     * 处理权限不足。
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        // 设置真正的 HTTP 状态码为 403
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Result<Void> result = Result.error(403, "没有权限访问该接口");

        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
