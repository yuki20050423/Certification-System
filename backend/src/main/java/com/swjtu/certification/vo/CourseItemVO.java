package com.swjtu.certification.vo;

import lombok.Data;

@Data
public class CourseItemVO {
    private Long id;                // 若为教学班，则为 teacherId；若为未来课程，则为 courseId
    private String type;            // "teacher" 或 "course"
    private String courseCode;      // 课程代码
    private String selectCode;      // 选课代码
    private String courseName;      // 课程名称
    private Integer credits;        // 学分
    private String semester;        // 学期（标准格式）
    private String preferred;       // 优选班级（来自教师表）
    private String teachingClass;   // 教学班号
    private String teacherName;     // 教师姓名（未来课程可设为“未开课”）
    private String enrollCount;    // 选课人数/状态
    private Boolean isFiled;        // 是否已备案
    private Boolean canFile;        // 是否可备案（未来课程可能不可备案）
}
