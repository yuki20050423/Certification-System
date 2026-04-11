package com.swjtu.certification.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 生成任务请求DTO
 */
@Data
public class GenerateTaskDTO {
    /**
     * 任务名称
     */
    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    /**
     * 开始时间 (ISO 格式，如 "2025-03-20T08:00")
     */
    @NotBlank(message = "开始时间不能为空")
    private String startTime;

    /**
     * 结束时间 (ISO 格式)
     */
    @NotBlank(message = "结束时间不能为空")
    private String endTime;

    /**
     * 审核员ID
     */
    private Long assessorId;

    private List<Long> teacherIds;

    /**
     * 选中的教学班ID列表（对应 teacher 表主键）
     */
    @NotEmpty(message = "请至少选择一个教学班")
    private List<Long> courseIds;

    /**
     * 选择的备案项目标识列表（如 ["syllabus", "achievement"]）
     */
    private List<String> items;

    private String futureCourseTeacher;
}
