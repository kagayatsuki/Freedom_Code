-- =============================================
-- 用户等级 & 每日用量限流 - 数据库迁移脚本
-- =============================================
USE agent;

-- 1. 修改 t_user.status 字段：TINYINT → INT，支持 0~3
ALTER TABLE t_user
    MODIFY COLUMN status INT NOT NULL DEFAULT 1
    COMMENT '账号状态：0-禁用 1-普通用户 2-升级版 3-专业版';

-- 2. 新建每日用量表
DROP TABLE IF EXISTS user_daily_usage;
CREATE TABLE user_daily_usage (
    id             BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL COMMENT '用户ID',
    usage_date     DATE   NOT NULL COMMENT '使用日期',
    project_count  INT    NOT NULL DEFAULT 0 COMMENT '当天新建项目数',
    modify_count   INT    NOT NULL DEFAULT 0 COMMENT '当天对话轮数',
    token_count    BIGINT NOT NULL DEFAULT 0 COMMENT '当天消耗的 LLM token 数（输入+输出）',
    UNIQUE KEY uk_user_date (user_id, usage_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日用量表';

-- 3. 已有数据库（不想 DROP 重建）可单独执行下面这条，补上 token_count 列
-- ALTER TABLE user_daily_usage
--     ADD COLUMN token_count BIGINT NOT NULL DEFAULT 0 COMMENT '当天消耗的 LLM token 数' AFTER modify_count;
