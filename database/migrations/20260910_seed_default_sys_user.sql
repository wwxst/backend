-- 本地开发默认系统用户：admin / 123456。
-- 密码仅以 BCrypt 密文写入，重复执行会更新该账号并保持启用。
INSERT INTO sys_user (username, password, nickname, status)
VALUES ('admin', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '系统管理员', 1)
ON DUPLICATE KEY UPDATE
    password = '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm',
    nickname = '系统管理员',
    status = 1;
