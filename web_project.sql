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
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username   VARCHAR(50)     NOT NULL COMMENT '登录账号',
    password   VARCHAR(100)    NOT NULL COMMENT 'BCrypt加密密码',
    nickname   VARCHAR(50)              DEFAULT NULL COMMENT '用户昵称',
    status     TINYINT          NOT NULL DEFAULT 1 COMMENT '状态：0禁用，1正常',
    created_at DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account_username (username),
    KEY idx_user_account_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '普通用户账号表';
