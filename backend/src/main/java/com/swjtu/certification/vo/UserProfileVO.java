package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 用户个人信息视图对象
 */
@Data
public class UserProfileVO {
    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

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

    /**
     * 院系
     */
    private String department;

    /**
     * 职称
     */
    private String title;

    /**
     * 角色
     */
    private String role;
}

