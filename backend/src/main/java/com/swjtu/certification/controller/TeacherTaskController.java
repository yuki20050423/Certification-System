package com.swjtu.certification.controller;

import com.swjtu.certification.service.TaskService;
import com.swjtu.certification.vo.Result;
import com.swjtu.certification.vo.TaskVO;
import com.swjtu.certification.vo.TeacherTaskStatisticsVO;
import com.swjtu.certification.vo.TeacherTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * 教师任务控制器
 */
@RestController
@RequestMapping("/api/teacher/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TeacherTaskController {
    @Autowired
    private TaskService taskService;

    /**
     * 获取教师的任务列表
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTeacherTasks(
            @RequestParam Long teacherId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "deadline") String sortBy,
            @RequestParam(defaultValue = "asc") String order,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 调用Service获取筛选后的任务列表
            List<TeacherTaskVO> taskList = taskService.getTeacherTasks(teacherId, status, sortBy, order);
            result.put("code", 200);
            result.put("msg", "获取任务列表成功");
            result.put("data", taskList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取任务列表失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取教师的任务详情
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<Map<String, Object>> getTeacherTaskDetail(
            @PathVariable Long taskId,
            @RequestParam Long teacherId,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 校验任务是否分配给当前教师（原有逻辑复用）
            TeacherTaskVO task = taskService.getTeacherTaskDetail(taskId, teacherId);
            if (task == null) {
                result.put("code", 404);
                result.put("msg", "任务不存在或无访问权限");
                return ResponseEntity.status(404).body(result);
            }
            result.put("code", 200);
            result.put("msg", "获取任务详情成功");
            result.put("data", task);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取任务详情失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 提交任务
     */
    @PostMapping("/{taskId}/submit")
    public ResponseEntity<Map<String, Object>> submitTask(
            @PathVariable Long taskId,
            @RequestParam Long teacherId,
            @RequestParam(required = false) String remark,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            taskService.submitTask(taskId, teacherId);
            result.put("code", 200);
            result.put("msg", "任务提交成功，等待审核");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "提交任务失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getTeacherTaskStatistics(@RequestParam Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        try {
            TeacherTaskStatisticsVO statistics = taskService.getTeacherTaskStatistics(teacherId);
            result.put("code", 200);
            result.put("msg", "获取任务统计成功");
            result.put("data", statistics);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}

