package com.swjtu.certification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 已备案档案视图对象
 */
@Data
public class ArchiveTaskVO {
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
     * 审核员ID
     */
    private Long assessorId;

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
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;

    /**
     * 年级
     */
    private String grade;

    /**
     * 学期
     */
    private String semester;

    /**
     * 专业
     */
    private String major;

    /**
     * 文件数量
     */
    private Integer fileCount;

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
