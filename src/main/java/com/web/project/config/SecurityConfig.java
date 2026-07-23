package com.web.project.config;

import com.web.project.config.security.RestAccessDeniedHandler;
import com.web.project.config.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    /**
     * 配置接口的访问权限。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                // 前后端分离接口暂时关闭 CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 关闭 Spring Security 默认登录页面
                .formLogin(AbstractHttpConfigurer::disable)

                // 关闭浏览器弹出的 Basic 登录框
                .httpBasic(AbstractHttpConfigurer::disable)

                /*
                 * JWT 不使用服务器 Session 保存登录状态。
                 * 每次请求都通过 Token 判断身份。
                 */
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(authorize -> authorize

                        // 管理员登录接口公开访问
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/admin/auth/login"
                        )
                        .permitAll()

                        // 普通用户登录接口公开访问
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/user/auth/login"
                        )
                        .permitAll()

                        // 所有管理端接口必须拥有 admin 权限
                        .requestMatchers("/api/admin/**")
                        .hasAuthority("SCOPE_admin")

                        // 所有普通用户端接口必须拥有 user 权限
                        .requestMatchers("/api/user/**")
                        .hasAuthority("SCOPE_user")

                        // 其他接口暂时要求登录
                        .anyRequest()
                        .authenticated()
                )
                /*
                 * 统一处理 Spring Security 的 401 和 403。
                 */
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
                )

                /*
                 * 开启 Spring Security 的 JWT Bearer 认证。
                 * 它会调用我们创建的 JwtDecoder。
                 */
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(
                                authenticationEntryPoint
                        )
                        .accessDeniedHandler(
                                accessDeniedHandler
                        )
                );

        return http.build();
    }
}
