DROP TABLE IF EXISTS redeem_record;
DROP TABLE IF EXISTS redeem_code;
DROP TABLE IF EXISTS redeem_code_batch;
DROP TABLE IF EXISTS user_subscription;
DROP TABLE IF EXISTS product_plan;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS user_account;
DROP TABLE IF EXISTS admin_user;

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

CREATE TABLE product
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    product_code VARCHAR(50)     NOT NULL COMMENT '商品编码',
    product_name VARCHAR(100)    NOT NULL COMMENT '商品名称',
    description  VARCHAR(1000)            DEFAULT NULL COMMENT '商品说明',
    status       TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '商品表';

CREATE TABLE product_plan
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '套餐ID',
    product_id    BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    plan_code     VARCHAR(50)     NOT NULL COMMENT '套餐编码',
    plan_name     VARCHAR(100)    NOT NULL COMMENT '套餐名称',
    duration_days INT UNSIGNED    NOT NULL COMMENT '有效天数',
    price         DECIMAL(10, 2)  NOT NULL COMMENT '销售价格',
    support_redeem  TINYINT       NOT NULL DEFAULT 1 COMMENT '是否支持兑换码：0否，1是',
    support_payment TINYINT       NOT NULL DEFAULT 0 COMMENT '是否支持在线支付：0否，1是',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    sort          INT             NOT NULL DEFAULT 0 COMMENT '排序值',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_plan_code (plan_code),
    KEY idx_product_plan_product_id (product_id),
    KEY idx_product_plan_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '商品套餐表';

CREATE TABLE user_subscription
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订阅ID',
    user_id          BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    product_id       BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    started_at       DATETIME        NOT NULL COMMENT '首次开通时间',
    expires_at       DATETIME        NOT NULL COMMENT '当前到期时间',
    status           TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0停用，1有效',
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_subscription_user_product (user_id, product_id),
    KEY idx_user_subscription_expires_at (expires_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '用户订阅表';

CREATE TABLE redeem_code_batch
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '批次ID',
    batch_no      VARCHAR(32)     NOT NULL COMMENT '批次编号',
    plan_id       BIGINT UNSIGNED NOT NULL COMMENT '绑定的商品套餐ID',
    quantity      INT UNSIGNED    NOT NULL COMMENT '生成数量',
    channel       VARCHAR(100)             DEFAULT NULL COMMENT '销售渠道',
    expires_at    DATETIME                 DEFAULT NULL COMMENT '整批兑换码过期时间',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0停用，1启用',
    created_by    BIGINT UNSIGNED NOT NULL COMMENT '创建管理员ID',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_redeem_code_batch_no (batch_no),
    KEY idx_redeem_code_batch_plan_id (plan_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '兑换码批次表';

CREATE TABLE redeem_code
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '兑换码ID',
    batch_id          BIGINT UNSIGNED NOT NULL COMMENT '批次ID',
    plan_id           BIGINT UNSIGNED NOT NULL COMMENT '商品套餐ID',
    code_hash         CHAR(64)        NOT NULL COMMENT '兑换码SHA-256哈希',
    code_masked       VARCHAR(32)     NOT NULL COMMENT '脱敏兑换码',
    status            TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0未兑换，1已兑换，2停用',
    expires_at        DATETIME                 DEFAULT NULL COMMENT '过期时间',
    redeemed_user_id  BIGINT UNSIGNED          DEFAULT NULL COMMENT '兑换用户ID',
    redeemed_at       DATETIME                 DEFAULT NULL COMMENT '兑换时间',
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_redeem_code_hash (code_hash),
    KEY idx_redeem_code_batch_id (batch_id),
    KEY idx_redeem_code_plan_id (plan_id),
    KEY idx_redeem_code_status (status),
    KEY idx_redeem_code_user_id (redeemed_user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '兑换码表';

CREATE TABLE redeem_record
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '兑换记录ID',
    redeem_code_id  BIGINT UNSIGNED NOT NULL COMMENT '兑换码ID',
    batch_id        BIGINT UNSIGNED NOT NULL COMMENT '兑换码批次ID',
    plan_id         BIGINT UNSIGNED NOT NULL COMMENT '商品套餐ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '兑换用户ID',
    plan_name       VARCHAR(100)    NOT NULL COMMENT '兑换时套餐名称快照',
    duration_days   INT UNSIGNED    NOT NULL COMMENT '兑换时有效天数快照',
    redeemed_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '兑换时间',
    redeem_ip       VARCHAR(45)              DEFAULT NULL COMMENT '兑换IP',
    PRIMARY KEY (id),
    UNIQUE KEY uk_redeem_record_code_id (redeem_code_id),
    KEY idx_redeem_record_user_id (user_id),
    KEY idx_redeem_record_plan_id (plan_id),
    KEY idx_redeem_record_redeemed_at (redeemed_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
    COMMENT = '兑换记录表';
