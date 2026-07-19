/**
  *创建数据库
**/
CREATE DATABASE web_project
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE web_project;

CREATE TABLE admin_user
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
    username   VARCHAR(20)  NOT NULL COMMENT '登录账号',
    password   VARCHAR(100) NOT NULL COMMENT '加密后的登录密码',
    nickname   VARCHAR(30)  NOT NULL COMMENT '管理员昵称',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常，0禁用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username)
) COMMENT = '后台管理员表'
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;