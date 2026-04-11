package com.swjtu.certification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审核员任务视图对象
 */
@Data
public class AssessorTaskVO {
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
     * 教师ID
     */
    private Long teacherId;

    /**
     * 教师工号
     */
    private String teacherWorkId;

    /**
     * 教师姓名
     */
    private String teacherName;

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
     * 提交时间（状态变为SUBMITTED的时间）
     */
    private LocalDateTime submitTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 年级
     */
    private String grade;

    /**
     * 学期
     */
    private String semester;
}

