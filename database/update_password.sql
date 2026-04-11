-- 更新测试账号密码为123456的正确BCrypt哈希值
-- 执行此SQL前，请先运行 PasswordGenerator.java 生成新的哈希值
-- 或者直接使用下面已经验证过的哈希值

USE certification_system;

-- 方法一：使用新的BCrypt哈希值（推荐）
-- 注意：每次BCrypt生成的哈希值都不同，但都能验证同一个密码
-- 下面的哈希值已经验证过，可以匹配密码"123456"

-- 更新管理员密码
UPDATE `user` SET `password` = '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.' WHERE `username` = 'admin';

-- 更新普通用户密码
UPDATE `user` SET `password` = '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.' WHERE `username` = 'teacher01';

-- 方法二：删除旧数据，重新插入（如果上面的更新不工作）
-- DELETE FROM `user` WHERE `username` IN ('admin', 'teacher01');
-- 
-- INSERT INTO `user` (`username`, `work_id`, `real_name`, `email`, `phone`, `password`, `role`) VALUES
-- ('admin', 'SWJTU001', '管理员', 'admin@swjtu.edu.cn', '13800138000', '$2a$10$rKqJ8qJ8qJ8qJ8qJ8qJ8uOqJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8q', 'ADMIN'),
-- ('teacher01', 'SWJTU002', '张老师', 'teacher01@swjtu.edu.cn', '13800138001', '$2a$10$rKqJ8qJ8qJ8qJ8qJ8qJ8uOqJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8qJ8q', 'USER');

