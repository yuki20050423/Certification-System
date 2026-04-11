package com.swjtu.certification.controller;

import com.swjtu.certification.dto.ReviewDTO;
import com.swjtu.certification.service.FileService;
import com.swjtu.certification.service.ReviewRecordService;
import com.swjtu.certification.service.TaskService;
import com.swjtu.certification.vo.AssessorTaskVO;
import com.swjtu.certification.vo.FileVO;
import com.swjtu.certification.vo.Result;
import com.swjtu.certification.vo.ReviewRecordVO;
import com.swjtu.certification.vo.ReviewStatisticsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 审核员控制器
 */
@RestController
@RequestMapping("/api/assessor")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class AssessorController {
    private final TaskService taskService;
    private final FileService fileService;
    private final ReviewRecordService reviewRecordService;

    /**
     * 获取审核员的任务列表
     */
    @GetMapping("/tasks")
    public Result<List<AssessorTaskVO>> getAssessorTasks(
            @RequestParam Long assessorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long teacherId) {
        try {
            List<AssessorTaskVO> tasks = taskService.getAssessorTasks(assessorId, status, teacherId);
            return Result.success("查询成功", tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取审核员的任务详情
     */
    @GetMapping("/tasks/{taskId}")
    public Result<AssessorTaskVO> getAssessorTaskDetail(
            @PathVariable Long taskId,
            @RequestParam Long assessorId) {
        try {
            AssessorTaskVO task = taskService.getAssessorTaskDetail(taskId, assessorId);
            return Result.success("查询成功", task);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取任务的文件列表
     */
    @GetMapping("/tasks/{taskId}/files")
    public Result<List<FileVO>> getTaskFiles(
            @PathVariable Long taskId,
            @RequestParam Long assessorId) {
        try {
            // 验证审核员是否有权限查看该任务
            taskService.getAssessorTaskDetail(taskId, assessorId);
            
            List<FileVO> files = fileService.getTaskFiles(taskId);
            return Result.success("查询成功", files);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 下载文件
     */
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId,
            @RequestParam Long assessorId) {
        try {
            // 获取文件信息以验证权限
            com.swjtu.certification.vo.FileVO fileInfo = fileService.getFileInfo(fileId);
            taskService.getAssessorTaskDetail(fileInfo.getTaskId(), assessorId);

            java.io.File file = fileService.getFileForDownload(fileId);
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileInfo.getFileType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + 
                            new String(fileInfo.getOriginalName().getBytes("UTF-8"), "ISO-8859-1") + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 预览文件（PDF、图片）
     */
    @GetMapping("/files/{fileId}/preview")
    public ResponseEntity<Resource> previewFile(
            @PathVariable Long fileId,
            @RequestParam Long assessorId) {
        try {
            // 获取文件信息以验证权限
            com.swjtu.certification.vo.FileVO fileInfo = fileService.getFileInfo(fileId);
            taskService.getAssessorTaskDetail(fileInfo.getTaskId(), assessorId);

            // 检查文件类型是否支持预览
            String fileType = fileInfo.getFileType();
            String extension = fileInfo.getFileExtension().toLowerCase();
            boolean canPreview = fileType.startsWith("image/") || 
                               fileType.equals("application/pdf") ||
                               extension.equals("pdf") ||
                               extension.equals("jpg") ||
                               extension.equals("jpeg") ||
                               extension.equals("png") ||
                               extension.equals("gif");

            if (!canPreview) {
                return ResponseEntity.badRequest().build();
            }

            java.io.File file = fileService.getFileForDownload(fileId);
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(fileInfo.getFileType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + 
                            new String(fileInfo.getOriginalName().getBytes("UTF-8"), "ISO-8859-1") + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 保存审核（进入审核中状态）
     */
    @PostMapping("/review/save")
    public Result<Object> saveReview(
            @RequestParam Long assessorId,
            @Valid @RequestBody ReviewDTO reviewDTO) {
        try {
            reviewRecordService.saveReviewRecord(assessorId, reviewDTO);
            return Result.success("保存成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 提交审核
     */
    @PostMapping("/review")
    public Result<Object> submitReview(
            @RequestParam Long assessorId,
            @Valid @RequestBody ReviewDTO reviewDTO) {
        try {
            reviewRecordService.createReviewRecord(assessorId, reviewDTO);
            return Result.success("审核提交成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取审核记录列表
     */
    @GetMapping("/reviews")
    public Result<List<ReviewRecordVO>> getReviewRecords(@RequestParam Long assessorId) {
        try {
            List<ReviewRecordVO> records = reviewRecordService.getAssessorReviewRecords(assessorId);
            return Result.success("查询成功", records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取任务的审核记录列表
     */
    @GetMapping("/tasks/{taskId}/reviews")
    public Result<List<ReviewRecordVO>> getTaskReviewRecords(
            @PathVariable Long taskId,
            @RequestParam Long assessorId) {
        try {
            // 验证审核员是否有权限查看该任务
            taskService.getAssessorTaskDetail(taskId, assessorId);
            
            List<ReviewRecordVO> records = reviewRecordService.getTaskReviewRecords(taskId);
            return Result.success("查询成功", records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取审核统计信息
     */
    @GetMapping("/statistics")
    public Result<ReviewStatisticsVO> getReviewStatistics(@RequestParam Long assessorId) {
        try {
            ReviewStatisticsVO statistics = taskService.getReviewStatistics(assessorId);
            return Result.success("查询成功", statistics);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}

