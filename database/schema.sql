-- 专业认证年度备案系统数据库表结构
-- 数据库名称：certification_system

-- 创建数据库
CREATE DATABASE IF NOT EXISTS certification_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE certification_system;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(300) NOT NULL COMMENT '用户名',
    `work_id` VARCHAR(300) NOT NULL COMMENT '工号',
    `real_name` VARCHAR(300) NOT NULL COMMENT '真实姓名',
    `email` VARCHAR(300) NOT NULL COMMENT '邮箱',
    `phone` VARCHAR(300) NOT NULL COMMENT '手机号',
    `password` VARCHAR(300) NOT NULL COMMENT '密码（加密后）',
    `role` VARCHAR(300) NOT NULL DEFAULT 'TEACHER' COMMENT '角色：ADMIN-管理员，ASSESSOR-审核员，TEACHER-教师',
    `status` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `department` VARCHAR(300) DEFAULT NULL COMMENT '院系',
    `title` VARCHAR(300) DEFAULT NULL COMMENT '职称',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_work_id` (`work_id`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_phone` (`phone`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 教师表（从教务系统获取的教师信息）
CREATE TABLE IF NOT EXISTS `teacher` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '教师ID',
    `semester` VARCHAR(300) NOT NULL COMMENT '学期（格式：YYYY-YYYY-N）',
    `select_code` VARCHAR(300) DEFAULT NULL COMMENT '选课代码',
    `course_code` VARCHAR(300) DEFAULT NULL COMMENT '课程代码',
    `course_name` VARCHAR(300) DEFAULT NULL COMMENT '课程名称',
    `teaching_class` VARCHAR(300) DEFAULT NULL COMMENT '教学班',
    `credits` TINYINT(3) DEFAULT 0 COMMENT '学分',
    `nature` VARCHAR(300) DEFAULT NULL COMMENT '课程性质',
    `department` VARCHAR(300) DEFAULT NULL COMMENT '院系',
    `teacher_name` VARCHAR(300) NOT NULL COMMENT '教师姓名',
    `title` VARCHAR(300) DEFAULT NULL COMMENT '职称',
    `time_location` TEXT DEFAULT NULL COMMENT '上课时间地点',
    `preferred` TEXT DEFAULT NULL COMMENT '是否优选',
    `status` VARCHAR(300) DEFAULT NULL COMMENT '状态',
    `campus` VARCHAR(300) DEFAULT NULL COMMENT '校区',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_semester` (`semester`),
    KEY `idx_course_name` (`course_name`),
    KEY `idx_teacher_name` (`teacher_name`),
    KEY `idx_department` (`department`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教师表（从教务系统获取）';

-- 插入测试数据（密码为：123456，已使用BCrypt加密）
-- 实际使用时请修改密码
-- 注意：如果登录失败，请执行 database/fix_password.sql 更新密码哈希值
INSERT INTO `user` (`username`, `work_id`, `real_name`, `email`, `phone`, `password`, `role`) VALUES
('admin', 'SWJTU001', '管理员', 'admin@swjtu.edu.cn', '13800138000', '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.', 'ADMIN'),
('teacher01', 'SWJTU002', '张老师', 'teacher01@swjtu.edu.cn', '13800138001', '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.', 'TEACHER'),
('teacher02', 'SWJTU003', '李老师', 'teacher02@swjtu.edu.cn', '13800138002', '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.', 'TEACHER'),
('teacher03', 'SWJTU006', '赵宏宇', '1690605744@qq.com', '13800138005', '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.', 'TEACHER'),
('assessor01', 'SWJTU004', '王评审员', '1902134507@qq.com', '13800138003', '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.', 'ASSESSOR'),
('assessor02', 'SWJTU005', '李评审员', 'assessor02@swjtu.edu.cn', '13800138004', '$2a$10$Lb55CXRs/D5DlvousCxB2.EY8fS9mR14DcxrueQ8cCDlkJRQ3Ie8.', 'ASSESSOR');

-- 课程表
CREATE TABLE IF NOT EXISTS `course` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '课程ID',
    `course_name` VARCHAR(255) NOT NULL COMMENT '课程名称',
    `grade` VARCHAR(20) NOT NULL COMMENT '年级',
    `major` VARCHAR(100) DEFAULT NULL COMMENT '专业',
    `semester` VARCHAR(20) NOT NULL COMMENT '学期（标准格式：YYYY-YYYY-N）',
    `status` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '状态：0-未指派，1-已指派',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_grade_semester` (`grade`, `semester`),
    KEY `idx_status` (`status`),
    KEY `idx_major` (`major`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

-- 任务表
CREATE TABLE IF NOT EXISTS `task` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `course_id` BIGINT(20) NOT NULL COMMENT '课程ID',
    `teacher_id` BIGINT(20) NULL COMMENT '教师ID（负责人）',
    `assessor_id` BIGINT(20) NULL COMMENT '审核员ID',
    `deadline` DATETIME NOT NULL COMMENT '截止日期',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING_UPLOAD' COMMENT '任务状态：PENDING_UPLOAD-待上传，SUBMITTED-已提交，REVIEWING-审核中，APPROVED-审核通过，NEED_REVISION-需修改',
    `description` TEXT DEFAULT NULL COMMENT '任务说明',
    `material_requirements` TEXT DEFAULT NULL COMMENT '材料要求说明',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_course_id` (`course_id`),
    KEY `idx_teacher_id` (`teacher_id`),
    KEY `idx_assessor_id` (`assessor_id`),
    KEY `idx_status` (`status`),
    KEY `idx_deadline` (`deadline`),
    CONSTRAINT `fk_task_course` FOREIGN KEY (`course_id`) REFERENCES `teacher` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_teacher` FOREIGN KEY (`teacher_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_assessor` FOREIGN KEY (`assessor_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务表';

-- 文件表
CREATE TABLE IF NOT EXISTS `file` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `task_id` BIGINT(20) NOT NULL COMMENT '任务ID',
    `file_name` VARCHAR(255) NOT NULL COMMENT '文件名',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `file_path` VARCHAR(500) NOT NULL COMMENT '文件存储路径',
    `file_size` BIGINT(20) NOT NULL COMMENT '文件大小（字节）',
    `file_type` VARCHAR(255) NOT NULL COMMENT '文件类型（MIME类型）',
    `file_extension` VARCHAR(20) NOT NULL COMMENT '文件扩展名',
    `upload_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `upload_user_id` BIGINT(20) NOT NULL COMMENT '上传用户ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT '文件状态：UPLOADED-已上传，DELETED-已删除',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_upload_user_id` (`upload_user_id`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_file_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_file_user` FOREIGN KEY (`upload_user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- 审核记录表
CREATE TABLE IF NOT EXISTS `review_record` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '审核记录ID',
    `task_id` BIGINT(20) NOT NULL COMMENT '任务ID',
    `assessor_id` BIGINT(20) NOT NULL COMMENT '审核员ID',
    `item_code` VARCHAR(20) DEFAULT NULL COMMENT '备案项目编码',
    `item_name` VARCHAR(255) DEFAULT NULL COMMENT '备案项目名称',
    `review_status` VARCHAR(20) NOT NULL COMMENT '审核状态：APPROVED-审核通过，REJECTED-需修改',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '评分（0-100）',
    `comment` TEXT DEFAULT NULL COMMENT '审核意见',
    `suggestions` TEXT DEFAULT NULL COMMENT '修改建议',
    `review_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_assessor_id` (`assessor_id`),
    KEY `idx_review_status` (`review_status`),
    CONSTRAINT `fk_review_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_assessor` FOREIGN KEY (`assessor_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核记录表';

-- 任务审核项目表
CREATE TABLE IF NOT EXISTS `task_review_item` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '任务审核项目ID',
    `task_id` BIGINT(20) NOT NULL COMMENT '任务ID',
    `item_code` VARCHAR(20) NOT NULL COMMENT '备案项目编码',
    `item_name` VARCHAR(255) NOT NULL COMMENT '备案项目名称',
    `assessor_id` BIGINT(20) NOT NULL COMMENT '审核员ID',
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待审核，REVIEWING-审核中，APPROVED-审核通过，REJECTED-需修改，CANCELLED-已取消',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_assessor_id` (`assessor_id`),
    KEY `idx_item_code` (`item_code`),
    KEY `idx_status` (`status`),
    CONSTRAINT `fk_task_review_item_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_task_review_item_assessor` FOREIGN KEY (`assessor_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务审核项目表';

-- 通知表
CREATE TABLE IF NOT EXISTS `notification` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    `user_id` BIGINT(20) NOT NULL COMMENT '接收用户ID',
    `task_id` BIGINT(20) DEFAULT NULL COMMENT '关联任务ID',
    `type` VARCHAR(50) NOT NULL COMMENT '通知类型：NEW_TASK-新任务，REVIEW_RESULT-审核结果，TASK_REMINDER-任务提醒',
    `title` VARCHAR(200) NOT NULL COMMENT '通知标题',
    `content` TEXT NOT NULL COMMENT '通知内容',
    `is_read` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读，1-已读',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_is_read` (`is_read`),
    KEY `idx_type` (`type`),
    CONSTRAINT `fk_notification_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_notification_task` FOREIGN KEY (`task_id`) REFERENCES `task` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表';
