package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 登录响应VO
 */
@Data
public class LoginVO {
    /**
     * Token（实际项目中应使用JWT）
     */
    private String token;

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    /**
     * 跳转路径
     */
    private String redirectUrl;

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String workId;
        private String realName;
        private String email;
        private String phone;
        private String role;
    }
}

