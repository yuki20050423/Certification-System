package com.swjtu.certification.vo;

import lombok.Data;

@Data
public class TeacherTaskStatisticsVO {
    private Long totalTasks;            // 总任务数
    private Long pendingUploadTasks;    // 待上传
    private Long submittedTasks;        // 已提交
    private Long reviewingTasks;        // 审核中
    private Long approvedTasks;         // 审核通过
    private Long needRevisionTasks;     // 需修改
    private Double completionRate;      // 完成率（审核通过 / 总任务）
}
