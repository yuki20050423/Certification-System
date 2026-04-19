package com.swjtu.certification.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件视图对象
 */
@Data
public class FileVO {
    /**
     * 文件ID
     */
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 文件路径
     */
    private String filePath;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件类型（MIME类型）
     */
    private String fileType;

    /**
     * 文件扩展名
     */
    private String fileExtension;

    /**
     * 上传时间
     */
    private LocalDateTime uploadTime;

    /**
     * 上传用户ID
     */
    private Long uploadUserId;

    /**
     * 上传用户姓名
     */
    private String uploadUserName;

    /**
     * 文件状态
     */
    private String status;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 下载URL
     */
    private String downloadUrl;

    /**
     * 格式化后的文件大小
     */
    private String fileSizeFormatted;

    /**
     * 所属备案项目编码
     */
    private String itemCode;

    /**
     * 所属备案项目名称
     */
    private String itemName;

    /**
     * 当前教师是否仍可编辑该文件
     */
    private Boolean canEdit;
}
