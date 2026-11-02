CREATE DATABASE IF NOT EXISTS agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE agent;

DROP TABLE IF EXISTS t_user;

CREATE TABLE t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    email       VARCHAR(128) NOT NULL COMMENT '邮箱',
    password    VARCHAR(256) NOT NULL COMMENT '加密密码',
    image       VARCHAR(512)          DEFAULT NULL COMMENT '头像URL，null时由服务层返回默认头像',
    status      TINYINT      NOT NULL DEFAULT 1  COMMENT '账号状态：1-正常 0-禁用',
    last_login  DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
