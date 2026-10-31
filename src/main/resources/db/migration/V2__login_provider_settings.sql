-- Split login availability from registration, password reset and account binding.
-- Keep the legacy auth.* channel keys for rollback compatibility; application login
-- checks now use the dedicated login keys only.
INSERT INTO sys_config (config_key, config_value, description)
VALUES
    ('auth.phone.login.enabled',
     (SELECT config_value FROM sys_config WHERE config_key = 'auth.phone.enabled'),
     '是否允许普通用户使用手机号登录'),
    ('auth.email.login.enabled',
     (SELECT config_value FROM sys_config WHERE config_key = 'auth.email.enabled'),
     '是否允许普通用户使用邮箱登录'),
    ('auth.sms.provider', 'aliyun', '当前选用的短信服务平台：aliyun 或 tencent'),
    ('auth.sms.aliyun.access-key-id', '', '阿里云短信 AccessKey ID'),
    ('auth.sms.aliyun.access-key-secret', '', '阿里云短信 AccessKey Secret 的 AES-GCM 加密密文'),
    ('auth.sms.aliyun.sign-name', '', '阿里云短信签名名称'),
    ('auth.sms.aliyun.template-code', '', '阿里云短信验证码模板 Code，模板变量使用 code'),
    ('auth.sms.tencent.secret-id', '', '腾讯云短信 SecretId'),
    ('auth.sms.tencent.secret-key', '', '腾讯云短信 SecretKey 的 AES-GCM 加密密文'),
    ('auth.sms.tencent.sdk-app-id', '', '腾讯云短信 SDK App ID'),
    ('auth.sms.tencent.sign-name', '', '腾讯云短信签名名称'),
    ('auth.sms.tencent.template-id', '', '腾讯云短信验证码模板 ID'),
    ('auth.sms.tencent.region', 'ap-guangzhou', '腾讯云短信接入地域'),
    ('auth.email.smtp.host', '', '邮箱 SMTP 服务器地址'),
    ('auth.email.smtp.port', '465', '邮箱 SMTP 服务器端口'),
    ('auth.email.smtp.username', '', '邮箱 SMTP 登录用户名'),
    ('auth.email.smtp.password', '', '邮箱 SMTP 密码或授权码的 AES-GCM 加密密文'),
    ('auth.email.smtp.from-address', '', '邮箱验证码发件地址'),
    ('auth.email.smtp.from-name', 'Atlas', '邮箱验证码发件人名称'),
    ('auth.email.smtp.security', 'SSL', 'SMTP 加密方式：SSL、STARTTLS 或 NONE');
