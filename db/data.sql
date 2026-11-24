-- 种子数据，先跑 schema.sql 再跑这个
-- mysql -uroot -p food_supply < db/data.sql
-- 演示账号（密码都是 BCrypt 密文，不能直接看出明文）：
--   admin      / admin123      管理员，全系统只有这一个
--   buyer01    / buyer123      采购员
--   supplier01 / supplier123   供货商，鲜丰食品有限公司
--   supplier02 / supplier123   供货商，绿源蔬菜基地

SET NAMES utf8mb4;

INSERT INTO sys_role (role_code, role_name, description) VALUES
('ADMIN',     '系统管理员', '收货商老板，全功能，负责审核账号'),
('PURCHASER', '采购员',     '建计划、发送、收货确认'),
('SUPPLIER',  '供货商',     '维护自有商品与单价、接单、发货、标记已送达');

-- 权限按操作粒度拆，接口上用 @PreAuthorize 逐个卡
INSERT INTO sys_permission (perm_code, perm_name, module) VALUES
('user:list',            '查看用户',        'user'),
('user:create',          '创建用户',        'user'),
('user:update',          '修改用户',        'user'),
('user:delete',          '删除用户',        'user'),
('user:audit',           '审核注册申请',    'user'),
('user:assign-role',     '分配角色',        'user'),

('role:list',            '查看角色',        'role'),
('role:create',          '创建角色',        'role'),
('role:update',          '修改角色',        'role'),
('role:delete',          '删除角色',        'role'),
('role:assign-permission','分配权限',       'role'),
('permission:list',      '查看权限',        'permission'),

('supplier:read',        '查看供货商',      'supplier'),
('supplier:create',      '新增供货商',      'supplier'),
('supplier:update',      '修改供货商',      'supplier'),
('supplier:delete',      '删除供货商',      'supplier'),

('product:read',         '查看商品',        'product'),
('product:create',       '新增商品',        'product'),
('product:update',       '修改商品与单价',  'product'),
('product:delete',       '删除商品',        'product'),

('plan:read',            '查看采购计划',    'plan'),
('plan:create',          '创建采购计划',    'plan'),
('plan:update',          '修改采购计划',    'plan'),
('plan:send',            '发送采购计划并拆单','plan'),
('plan:cancel',          '取消采购计划',    'plan'),
('plan:delete',          '删除采购计划',    'plan'),

('order:read',           '查看子单',        'order'),
('order:confirm',        '供货商接单',      'order'),
('order:reject',         '供货商驳回',      'order'),
('order:deliver',        '标记已送达',      'order'),
('order:receive',        '确认已收货',      'order'),
('order:not-delivered',  '标记未送达',      'order'),
('order:cancel',         '强制取消子单',    'order'),

('settlement:view',      '查看月结累计金额','settlement');

-- 管理员拿全部
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- 采购员
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'PURCHASER'
  AND p.perm_code IN (
    'supplier:read', 'product:read',
    'plan:read', 'plan:create', 'plan:update', 'plan:send', 'plan:cancel', 'plan:delete',
    'order:read', 'order:receive', 'order:not-delivered', 'order:cancel',
    'settlement:view'
);

-- 供货商
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'SUPPLIER'
  AND p.perm_code IN (
    'supplier:read',
    'product:read', 'product:create', 'product:update',
    'order:read', 'order:confirm', 'order:reject', 'order:deliver',
    'settlement:view'
);

INSERT INTO supplier (name, contact_person, phone, address, status, remark) VALUES
('鲜丰食品有限公司', '李鲜', '13800000001', '城东农产品批发市场 A 区 12 号', 'ACTIVE', '主营猪肉、禽蛋'),
('绿源蔬菜基地',     '王绿', '13800000002', '城郊绿源农业园区 3 号棚',     'ACTIVE', '主营时令蔬菜'),
('恒达粮油贸易',     '赵恒', '13800000003', '粮油批发市场 B 区 7 号',       'ACTIVE', '主营米面粮油');

-- 密码：admin123 / buyer123 / supplier123
INSERT INTO sys_user (username, password, real_name, phone, status, applied_role, supplier_id) VALUES
('admin',     '$2a$10$qDeDuIDff9djYe1vDzXZZeEtW4fRjXZG8QvhCuKhV9q1xoRgFLm/a', '王老板', '13900000001', 'ACTIVE', NULL, NULL),
('buyer01',   '$2a$10$a1uPAY4JVyF9lkz672N/ze8aUD3sT78PnfsrPmq1/gmQ/PvtrrvYe', '张采购', '13900000002', 'ACTIVE', 'PURCHASER', NULL),
('supplier01','$2a$10$K89ZM8kMd9XdL28iVC/qL.ky1dYdp7gNbuK6Y6MZOhqOJz3wbWBZ2', '李鲜',   '13900000003', 'ACTIVE', 'SUPPLIER',
 (SELECT id FROM (SELECT id FROM supplier WHERE name = '鲜丰食品有限公司') t)),
('supplier02','$2a$10$K89ZM8kMd9XdL28iVC/qL.ky1dYdp7gNbuK6Y6MZOhqOJz3wbWBZ2', '王绿',   '13900000004', 'ACTIVE', 'SUPPLIER',
 (SELECT id FROM (SELECT id FROM supplier WHERE name = '绿源蔬菜基地') t));

-- 按业务码关联，不写死自增 id，重跑也不会错位
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'admin'     AND r.role_code = 'ADMIN';
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'buyer01'   AND r.role_code = 'PURCHASER';
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'supplier01' AND r.role_code = 'SUPPLIER';
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r WHERE u.username = 'supplier02' AND r.role_code = 'SUPPLIER';

INSERT INTO product (supplier_id, name, category, unit, price, stock_quantity, status) VALUES
((SELECT id FROM (SELECT id FROM supplier WHERE name = '鲜丰食品有限公司') t), '五花肉',   '肉类', '斤', 32.50, 120.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '鲜丰食品有限公司') t), '猪排骨',   '肉类', '斤', 38.00,  80.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '鲜丰食品有限公司') t), '鲜鸡蛋',   '禽蛋', '箱', 96.00,  45.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '绿源蔬菜基地') t),     '土豆',     '蔬菜', '斤',  3.20, 300.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '绿源蔬菜基地') t),     '大白菜',   '蔬菜', '斤',  2.10, 400.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '绿源蔬菜基地') t),     '西红柿',   '蔬菜', '斤',  5.60, 150.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '恒达粮油贸易') t),     '东北大米', '粮油', '斤',  4.80, 800.00, 'ON_SALE'),
((SELECT id FROM (SELECT id FROM supplier WHERE name = '恒达粮油贸易') t),     '花生油',   '粮油', '桶', 78.00,  60.00, 'ON_SALE');
