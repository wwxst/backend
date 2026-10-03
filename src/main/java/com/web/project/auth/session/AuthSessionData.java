package com.web.project.auth.session;

/**
 * Redis 会话数据。
 *
 * passwordVersion 只在普通用户会话中保存，系统用户会话为 null。
 *
 * @param accountId       账号主键
 * @param passwordVersion 普通用户密码版本
 */
public record AuthSessionData(
        Long accountId,
        Long passwordVersion
) {
}
