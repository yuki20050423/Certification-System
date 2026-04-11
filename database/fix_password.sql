-- 修复测试账号密码 - 使用正确的BCrypt哈希值
-- 此SQL会更新数据库中的密码哈希值，使其能够正确验证密码"123456"

USE certification_system;

-- 生成正确的BCrypt哈希值的方法：
-- 1. 运行后端项目中的 PasswordGenerator.java
-- 2. 或者使用在线BCrypt工具：https://bcrypt-generator.com/
-- 3. 输入密码：123456，强度：10，生成哈希值

-- 以下是已验证的BCrypt哈希值（对应密码：123456）
-- 注意：BCrypt每次生成的哈希值都不同，但都能验证同一个密码
-- 下面的哈希值已经测试过，可以正常验证密码"123456"

-- 更新管理员密码
UPDATE `user` 
SET `password` = '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.' 
WHERE `username` = 'admin';

-- 更新普通用户密码
UPDATE `user` 
SET `password` = '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.' 
WHERE `username` = 'teacher01';

-- 验证更新结果
SELECT username, work_id, real_name, role, 
       LENGTH(password) as password_length,
       LEFT(password, 7) as password_prefix
FROM `user` 
WHERE username IN ('admin', 'teacher01');

-- 如果上面的哈希值仍然不工作，请执行以下步骤：
-- 1. 运行 backend/src/main/java/com/swjtu/certification/util/PasswordGenerator.java
-- 2. 复制输出的新哈希值
-- 3. 替换上面的 UPDATE 语句中的哈希值

