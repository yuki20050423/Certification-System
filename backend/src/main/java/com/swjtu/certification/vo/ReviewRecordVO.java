package com.swjtu.certification.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审核记录视图对象
 */
@Data
public class ReviewRecordVO {
    /**
     * 审核记录ID
     */
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 教学班
     */
    private String teachingClass;

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
     * 备案项目编码
     */
    private String itemCode;

    /**
     * 备案项目名称
     */
    private String itemName;

    /**
     * 审核状态
     */
    private String reviewStatus;

    /**
     * 审核状态描述
     */
    private String reviewStatusDesc;

    /**
     * 评分
     */
    private BigDecimal score;

    /**
     * 审核意见
     */
    private String comment;

    /**
     * 修改建议
     */
    private String suggestions;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;

    /**
     * 审核员姓名（备用）
     */
    private String reviewerName;

    /**
     * 审核员工号
     */
    private String reviewerWorkId;
}

