package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 课程视图对象
 */
@Data
public class CourseVO {
    /**
     * 课程ID
     */
    private Long id;


    private String courseName;

    /**
     * 年级
     */
    private String grade;

    /**
     * 专业
     */
    private String major;

    /**
     * 学期（标准格式：YYYY-YYYY-N）
     */
    private String semester;

    /**
     * 状态：0-未指派，1-已指派
     */
    private Integer status;

    /**
     * 状态描述
     */
    private String statusDesc;
}

