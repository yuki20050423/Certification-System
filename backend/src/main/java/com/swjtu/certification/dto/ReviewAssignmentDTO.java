package com.swjtu.certification.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * 审核任务分配DTO
 */
@Data
public class ReviewAssignmentDTO {

    /**
     * 需要分配审核的任务ID列表
     */
    @NotEmpty(message = "任务列表不能为空")
    private List<Long> taskIds;

    /**
     * 需要审核的项目列表（如：大纲专项、达成度专项等）
     */
    @NotEmpty(message = "审核项目不能为空")
    private List<String> reviewProjects;

    /**
     * 审核人分配信息
     * Key: 审核人ID
     * Value: 该审核人负责审核的任务ID列表
     */
    @NotEmpty(message = "审核人分配不能为空")
    private Map<Long, List<Long>> assessorAssignments;

    /**
     * 截止日期
     */
    @NotNull(message = "截止日期不能为空")
    private String deadline;
}
