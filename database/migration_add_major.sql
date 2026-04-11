-- 为course表添加major列的迁移脚本
-- 执行此脚本前请确保已备份数据库

USE certification_system;

-- 添加major列
ALTER TABLE `course` ADD COLUMN `major` VARCHAR(100) DEFAULT NULL COMMENT '专业' AFTER `grade`;

-- 添加索引
ALTER TABLE `course` ADD INDEX `idx_major` (`major`);

-- 验证修改
SELECT 'major列已成功添加到course表' AS message;
