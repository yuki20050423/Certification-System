package com.swjtu.certification.vo;

import lombok.Data;

/**
 * 审核统计视图对象
 */
@Data
public class ReviewStatisticsVO {
    /**
     * 总任务数
     */
    private Long totalTasks;

    /**
     * 待审核任务数
     */
    private Long pendingTasks;

    /**
     * 审核中任务数
     */
    private Long reviewingTasks;

    /**
     * 审核通过任务数
     */
    private Long approvedTasks;

    /**
     * 需修改任务数
     */
    private Long needRevisionTasks;

    /**
     * 通过率（百分比）
     */
    private Double approvalRate;

    /**
     * 已处理任务数
     */
    private Long processedTasks;
}

