package com.swjtu.certification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师任务视图对象
 */
@Data
public class TeacherTaskVO {
    /**
     * 任务ID
     */
    private Long id;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 教学班号
     */
    private String teachingClass;

    /**
     * 年级
     */
    private String grade;

    /**
     * 学期
     */
    private String semester;

    /**
     * 截止日期
     */
    private LocalDateTime deadline;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 任务状态描述
     */
    private String statusDesc;

    /**
     * 任务说明
     */
    private String description;

    /**
     * 材料要求说明
     */
    private String materialRequirements;

    /**
     * 已上传文件数量
     */
    private Integer fileCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 是否已过期
     */
    private Boolean isExpired;
}

