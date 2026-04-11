package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 教师视图对象
 */
@Data
public class TeacherVO {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 工号
     */
    private String workId;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;
}

