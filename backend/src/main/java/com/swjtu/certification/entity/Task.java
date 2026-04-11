package com.swjtu.certification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务实体类
 */
@Data
@TableName("task")
public class Task {
    /**
     * 任务ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 教师ID（负责人）
     */
    private Long teacherId;

    /**
     * 审核员ID
     */
    private Long assessorId;

    /**
     * 截止日期
     */
    private LocalDateTime deadline;

    /**
     * 任务状态：PENDING_UPLOAD-待上传，SUBMITTED-已提交，REVIEWING-审核中，APPROVED-审核通过，NEED_REVISION-需修改
     */
    private String status;

    /**
     * 任务说明
     */
    private String description;

    /**
     * 材料要求说明
     */
    private String materialRequirements;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

