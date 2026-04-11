package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 用户视图对象
 */
@Data
public class UserVO {
    private Long id;
    private Long teacherId;
    private String username;
    private String workId;
    private String realName;
    private String email;
    private String phone;
    private String role;
    private String department;
    private String title;
    private Integer status;
    private Boolean exists;
    private Boolean isNewUser;
    private String teachingClass;
    private String courseName;
    private String message;
}
