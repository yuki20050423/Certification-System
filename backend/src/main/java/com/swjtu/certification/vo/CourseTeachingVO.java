package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 课程教学班视图对象（用于前端课程列表展示）
 */
@Data
public class CourseTeachingVO {
    /**
     * 教学班ID（对应 teacher 表的主键）
     */
    private Long id;

    /**
     * 课程代码
     */
    private String courseCode;

    /**
     * 选课代码
     */
    private String selectCode;

    /**
     * 课程名称
     */
    private String courseName;

    /**
     * 学分
     */
    private Integer credits;

    /**
     * 学期（标准格式 YYYY-YYYY-N）
     */
    private String semester;

    /**
     * 优选班级信息（preferred 字段）
     */
    private String preferred;

    /**
     * 教学班号
     */
    private String teachingClass;

    /**
     * 教师姓名
     */
    private String teacherName;

    /**
     * 选课人数/状态
     */
    private String enrollCount;

    /**
     * 是否已备案（true=已备案，false=未备案）
     */
    private Boolean isFiled;
}
