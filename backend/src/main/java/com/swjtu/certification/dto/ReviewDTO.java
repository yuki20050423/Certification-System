package com.swjtu.certification.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 审核操作DTO
 */
@Data
public class ReviewDTO {
    /**
     * 任务ID
     */
    @NotNull(message = "任务ID不能为空")
    private Long taskId;

    /**
     * 审核状态：APPROVED-审核通过，REJECTED-需修改
     */
    @NotBlank(message = "审核状态不能为空")
    private String reviewStatus;

    /**
     * 评分（0-100，可选）
     */
    private BigDecimal score;

    /**
     * 审核意见
     */
    private String comment;

    /**
     * 修改建议（当审核状态为REJECTED时必填）
     */
    private String suggestions;
}

