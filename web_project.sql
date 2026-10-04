/**
  *创建数据库
**/
CREATE DATABASE web_project
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE web_project;

CREATE TABLE sys_user
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '系统用户ID',
    username   VARCHAR(20)  NOT NULL COMMENT '登录账号',
    password   VARCHAR(100) NOT NULL COMMENT '加密后的登录密码',
    nickname   VARCHAR(30)  NOT NULL COMMENT '系统用户昵称',
    avatar     VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) COMMENT = '后台系统用户表'
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

INSERT INTO sys_user (username, password, nickname, status)
VALUES ('admin', '$2a$10$gqznYBmp4FWgfAFVhrpRReRv.YI0EE4Ysyy46.Ts186.SMw7HRlFm', '系统管理员', 1);

CREATE TABLE user_account
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    user_no          VARCHAR(32)     NOT NULL COMMENT '对外展示号码，不参与登录和鉴权',
    phone            VARCHAR(20)              DEFAULT NULL COMMENT '中国大陆手机号',
    email            VARCHAR(100)             DEFAULT NULL COMMENT '邮箱',
    password         VARCHAR(100)             DEFAULT NULL COMMENT 'BCrypt加密密码；验证码注册时可为空',
    password_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '密码版本，修改或重置密码时递增',
    nickname         VARCHAR(50)              DEFAULT NULL COMMENT '用户昵称',
    avatar           VARCHAR(255)             DEFAULT NULL COMMENT '头像地址',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1正常',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_user_no (user_no),
    UNIQUE KEY uk_user_account_phone (phone),
    UNIQUE KEY uk_user_account_email (email),
    KEY idx_user_account_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '普通用户账号表';

CREATE TABLE sys_config
(
    config_key   VARCHAR(100) NOT NULL COMMENT '配置项键',
    config_value VARCHAR(255) NOT NULL COMMENT '配置项值',
    description  VARCHAR(255)          DEFAULT NULL COMMENT '配置说明',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (config_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '系统配置表';

INSERT INTO sys_config (config_key, config_value, description)
VALUES ('auth.phone.enabled', 'true', '手机号注册、登录和绑定是否可用'),
       ('auth.phone.code.enabled', 'true', '手机验证码是否可用'),
       ('auth.email.enabled', 'true', '邮箱注册、登录和绑定是否可用'),
       ('auth.email.code.enabled', 'true', '邮箱验证码是否可用');
