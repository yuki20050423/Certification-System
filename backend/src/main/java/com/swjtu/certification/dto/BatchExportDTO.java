package com.swjtu.certification.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量导出DTO
 */
@Data
public class BatchExportDTO {
    /**
     * 任务ID列表
     */
    private List<Long> taskIds;
}
