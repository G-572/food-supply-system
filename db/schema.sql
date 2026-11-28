-- 食品供货订单管理系统 建表脚本
-- MySQL 8.4，utf8mb4
-- 会先 DROP 再建，只用于初始化，已有数据会被清掉
-- 用法: mysql -uroot -p < db/schema.sql

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS purchase_order_item;
DROP TABLE IF EXISTS purchase_order;
DROP TABLE IF EXISTS purchase_plan_item;
DROP TABLE IF EXISTS purchase_plan;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS supplier;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;

-- 角色
CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    role_code   VARCHAR(50)  NOT NULL COMMENT 'ADMIN / PURCHASER / SUPPLIER',
    role_name   VARCHAR(50)  NOT NULL COMMENT '角色名称',
    description VARCHAR(255)          DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (role_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色表';

-- 权限，细到操作级，比如 order:confirm
CREATE TABLE sys_permission (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    perm_code  VARCHAR(80) NOT NULL COMMENT '权限编码',
    perm_name  VARCHAR(80) NOT NULL COMMENT '权限名称',
    module     VARCHAR(50) NOT NULL COMMENT '所属模块',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_code (perm_code),
    KEY idx_perm_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '权限表';

-- 供货商。收货商只有一家，不建表
CREATE TABLE supplier (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL COMMENT '供货商名称',
    contact_person VARCHAR(50)           DEFAULT NULL,
    phone          VARCHAR(20)           DEFAULT NULL,
    address        VARCHAR(255)          DEFAULT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 合作中 / DISABLED 停用',
    remark         VARCHAR(255)          DEFAULT NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_name (name),
    KEY idx_supplier_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '供货商表';

-- 用户。token_version 用来让旧 token 失效（退出登录时 +1）
CREATE TABLE sys_user (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50) NOT NULL,
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，不存明文',
    real_name     VARCHAR(50)          DEFAULT NULL,
    phone         VARCHAR(20)          DEFAULT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACTIVE / DISABLED / REJECTED',
    applied_role  VARCHAR(50)          DEFAULT NULL COMMENT '注册时申请的意向角色',
    supplier_id   BIGINT               DEFAULT NULL COMMENT '供货商账号绑定的供货商',
    token_version INT         NOT NULL DEFAULT 0,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_user_status (status),
    KEY idx_user_supplier (supplier_id),
    CONSTRAINT fk_user_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

-- 用户角色，一个人可以挂多个角色
CREATE TABLE sys_user_role (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    role_id    BIGINT   NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_ur_role (role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '用户角色关联表';

CREATE TABLE sys_role_permission (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    role_id       BIGINT   NOT NULL,
    permission_id BIGINT   NOT NULL,
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_perm (role_id, permission_id),
    KEY idx_rp_perm (permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '角色权限关联表';

-- 商品。一个商品只属于一家供货商，拆单就是按这个 supplier_id 分组的
CREATE TABLE product (
    id             BIGINT         NOT NULL AUTO_INCREMENT,
    supplier_id    BIGINT         NOT NULL,
    name           VARCHAR(100)   NOT NULL,
    category       VARCHAR(50)             DEFAULT NULL COMMENT '蔬菜 / 肉类 / 粮油 ...',
    unit           VARCHAR(20)    NOT NULL DEFAULT '斤',
    price          DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '单价，供货商维护',
    stock_quantity DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT '库存',
    status         VARCHAR(20)    NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE / OFF_SALE',
    remark         VARCHAR(255)            DEFAULT NULL,
    created_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_supplier_name (supplier_id, name),
    KEY idx_product_supplier (supplier_id),
    KEY idx_product_status (status),
    CONSTRAINT fk_product_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '商品表';

-- 采购计划，采购员一天可建多张，一张可跨供货商
CREATE TABLE purchase_plan (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    plan_no      VARCHAR(32)    NOT NULL,
    purchaser_id BIGINT         NOT NULL,
    plan_date    DATE           NOT NULL,
    status       VARCHAR(20)    NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / SENT',
    total_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    remark       VARCHAR(255)            DEFAULT NULL,
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_no (plan_no),
    KEY idx_plan_purchaser (purchaser_id),
    KEY idx_plan_date (plan_date),
    CONSTRAINT fk_plan_purchaser FOREIGN KEY (purchaser_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '采购计划表';

-- 计划明细。草稿阶段就一行一个商品，发送时按商品归属的供货商分组搬到子单明细
CREATE TABLE purchase_plan_item (
    id         BIGINT         NOT NULL AUTO_INCREMENT,
    plan_id    BIGINT         NOT NULL,
    product_id BIGINT         NOT NULL,
    quantity   DECIMAL(12, 2) NOT NULL,
    created_at DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_plan_item_plan (plan_id),
    KEY idx_plan_item_product (product_id),
    CONSTRAINT fk_plan_item_plan FOREIGN KEY (plan_id) REFERENCES purchase_plan (id) ON DELETE CASCADE,
    CONSTRAINT fk_plan_item_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '采购计划明细表';

-- 子单。发送计划时按供货商拆出来，一单只对一家供货商
CREATE TABLE purchase_order (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    order_no             VARCHAR(32)    NOT NULL,
    plan_id              BIGINT         NOT NULL,
    supplier_id          BIGINT         NOT NULL COMMENT '由商品归属反查，不收前端传的值',
    status               VARCHAR(30)    NOT NULL DEFAULT 'PENDING_CONFIRM',
    total_amount         DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    not_delivered_reason VARCHAR(255)            DEFAULT NULL,
    confirmed_at         DATETIME                DEFAULT NULL,
    delivered_at         DATETIME                DEFAULT NULL,
    received_at          DATETIME                DEFAULT NULL COMMENT '月结按这个时间归月',
    created_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_plan (plan_id),
    KEY idx_order_supplier (supplier_id),
    KEY idx_order_status (status),
    KEY idx_order_received (status, received_at),
    CONSTRAINT fk_order_plan FOREIGN KEY (plan_id) REFERENCES purchase_plan (id),
    CONSTRAINT fk_order_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '子单表';

-- 子单明细。单价存快照，后面供货商改价不影响历史单
CREATE TABLE purchase_order_item (
    id           BIGINT         NOT NULL AUTO_INCREMENT,
    order_id     BIGINT         NOT NULL,
    product_id   BIGINT         NOT NULL,
    product_name VARCHAR(100)   NOT NULL COMMENT '名称快照',
    unit         VARCHAR(20)    NOT NULL,
    quantity     DECIMAL(12, 2) NOT NULL,
    unit_price   DECIMAL(12, 2) NOT NULL COMMENT '单价快照',
    subtotal     DECIMAL(14, 2) NOT NULL,
    created_at   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_item_order (order_id),
    KEY idx_item_product (product_id),
    CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES purchase_order (id),
    CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '子单明细表';

SET FOREIGN_KEY_CHECKS = 1;
