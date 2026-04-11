package com.swjtu.certification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Teacher {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String semester;

    private String selectCode;

    private String courseCode;

    private String courseName;

    private String teachingClass;

    private Byte credits;

    private String nature;

    private String department;

    private String teacherName;

    private String title;

    private String timeLocation;

    private String preferred;

    private String status;

    private String campus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
