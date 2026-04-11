package com.swjtu.certification.dto;

import lombok.Data;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 批量任务DTO
 */
@Data
public class BatchTaskDTO {
    /**
     * 任务列表
     */
    @NotEmpty(message = "任务列表不能为空")
    private List<TaskItem> tasks;

    /**
     * 任务项
     */
    @Data
    public static class TaskItem {
        /**
         * 课程ID（手动添加时可为null）
         */
        private Long courseId;

        /**
         * 教师ID
         */
        @NotNull(message = "教师ID不能为空")
        private Long teacherId;

        /**
         * 审核员ID（可为空）
         */
        private Long assessorId;   // 移除了 @NotNull

        /**
         * 截止日期
         */
        @NotNull(message = "截止日期不能为空")
        private LocalDateTime deadline;

        /**
         * 任务说明
         */
        private String description;

        /**
         * 材料要求
         */
        private String materialRequirements;

        /**
         * 课程名称（手动添加时使用）
         */
        private String courseName;

        /**
         * 教学班号（手动添加时使用）
         */
        private String teachingClass;

        /**
         * 是否为手动添加的任务
         */
        private Boolean isManual;
    }
}