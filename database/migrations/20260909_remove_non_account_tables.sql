-- 在目标数据库中手动执行；执行前备份，下列业务表及其数据将永久删除。
-- 保留 sys_user 与 user_account 两类账号表。
DROP TABLE IF EXISTS redeem_record;
DROP TABLE IF EXISTS redeem_code;
DROP TABLE IF EXISTS redeem_code_batch;
DROP TABLE IF EXISTS user_subscription;
DROP TABLE IF EXISTS product_plan;
DROP TABLE IF EXISTS product;
