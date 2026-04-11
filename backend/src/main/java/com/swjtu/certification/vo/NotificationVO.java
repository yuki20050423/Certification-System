package com.swjtu.certification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知视图对象
 */
@Data
public class NotificationVO {
    /**
     * 通知ID
     */
    private Long id;

    /**
     * 接收用户ID
     */
    private Long userId;

    /**
     * 接收用户姓名
     */
    private String userName;

    /**
     * 接收用户角色
     */
    private String userRole;

    /**
     * 关联任务ID
     */
    private Long taskId;

    /**
     * 通知类型
     */
    private String type;

    /**
     * 通知类型描述
     */
    private String typeDesc;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容
     */
    private String content;

    /**
     * 是否已读
     */
    private Integer isRead;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}

