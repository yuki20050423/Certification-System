package com.swjtu.certification.dto;

import lombok.Data;

/**
 * 更新个人信息DTO
 */
@Data
public class UpdateProfileDTO {
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
}

