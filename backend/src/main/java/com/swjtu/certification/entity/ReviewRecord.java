package com.swjtu.certification.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审核记录实体类
 */
@Data
@TableName("review_record")
public class ReviewRecord {
    /**
     * 审核记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务ID
     */
    private Long taskId;

    /**
     * 审核员ID
     */
    private Long assessorId;

    /**
     * 备案项目编码
     */
    private String itemCode;

    /**
     * 备案项目名称
     */
    private String itemName;

    /**
     * 审核状态：APPROVED-审核通过，REJECTED-需修改
     */
    private String reviewStatus;

    /**
     * 评分（0-100）
     */
    private BigDecimal score;

    /**
     * 审核意见
     */
    private String comment;

    /**
     * 修改建议
     */
    private String suggestions;

    /**
     * 审核时间
     */
    private LocalDateTime reviewTime;
}

