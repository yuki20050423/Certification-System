package com.swjtu.certification.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务视图对象
 */
@Data
public class TaskVO {
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
     * 课程代码
     */
    private String courseCode;

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
     * 审核员ID
     */
    private Long assessorId;

    /**
     * 审核员工号
     */
    private String assessorWorkId;

    /**
     * 审核员姓名
     */
    private String assessorName;

    /**
     * 截止日期
     */
    private LocalDateTime deadline;

    /**
     * 任务状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 任务说明
     */
    private String description;

    /**
     * 材料要求说明
     */
    private String materialRequirements;

    /**
     * 年级
     */
    private String grade;

    /**
     * 学期
     */
    private String semester;

    /**
     * 当前仍可分配/复审的审核项目
     */
    private List<ReviewProjectVO> availableReviewProjects;

    /**
     * 已分配或已审核过的审核项目摘要
     */
    private String reviewProjectsDescription;
}

