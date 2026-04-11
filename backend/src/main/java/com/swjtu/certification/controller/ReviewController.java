package com.swjtu.certification.controller;

import com.swjtu.certification.entity.ReviewRecord;
import com.swjtu.certification.service.ReviewRecordService;
import com.swjtu.certification.service.TaskService;
import com.swjtu.certification.vo.ReviewRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReviewController {
    private final ReviewRecordService reviewRecordService;
    private final TaskService taskService; // 用于权限校验

    /**
     * 获取任务的审核记录列表
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskReviewRecords(
            @PathVariable Long taskId,
            @RequestParam Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 校验任务归属（教师是否有权查看）
            taskService.getTeacherTaskDetail(taskId, teacherId);

            List<ReviewRecord> reviewList = reviewRecordService.getReviewRecordsByTaskId(taskId);
            result.put("code", 200);
            result.put("msg", "获取审核记录成功");
            result.put("data", reviewList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取审核记录失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取最新的审核记录
     */
    @GetMapping("/task/{taskId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestReviewRecord(
            @PathVariable Long taskId,
            @RequestParam Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 校验任务归属
            taskService.getTeacherTaskDetail(taskId, teacherId);

            ReviewRecordVO latestReview = reviewRecordService.getLatestReviewRecord(taskId);
            result.put("code", 200);
            result.put("msg", "获取最新审核记录成功");
            result.put("data", latestReview);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取最新审核记录失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/admin/task/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskReviewRecordsForAdmin(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ReviewRecordVO> reviewList = reviewRecordService.getTaskReviewRecords(taskId);
            result.put("code", 200);
            result.put("msg", "获取审核记录成功");
            result.put("data", reviewList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取审核记录失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 管理员获取最新的审核记录（无需teacherId校验）
     */
    @GetMapping("/admin/task/{taskId}/latest")
    public ResponseEntity<Map<String, Object>> getLatestReviewRecordForAdmin(@PathVariable Long taskId) {
        Map<String, Object> result = new HashMap<>();
        try {
            ReviewRecordVO latestReview = reviewRecordService.getLatestReviewRecord(taskId);
            result.put("code", 200);
            result.put("msg", "获取最新审核记录成功");
            result.put("data", latestReview);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取最新审核记录失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取当前教师的所有审核记录（教师端专用）
     */
    @GetMapping("/teacher")
    public ResponseEntity<Map<String, Object>> getTeacherReviewRecords(@RequestParam Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<ReviewRecordVO> reviewList = reviewRecordService.getTeacherReviewRecords(teacherId);
            result.put("code", 200);
            result.put("msg", "获取审核记录成功");
            result.put("data", reviewList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}

