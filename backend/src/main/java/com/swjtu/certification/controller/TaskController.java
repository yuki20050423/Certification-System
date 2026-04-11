package com.swjtu.certification.controller;

import com.swjtu.certification.dto.BatchTaskDTO;
import com.swjtu.certification.dto.TaskUpdateDTO;
import com.swjtu.certification.service.TaskService;
import com.swjtu.certification.vo.Result;
import com.swjtu.certification.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 任务控制器
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class TaskController {
    private final TaskService taskService;

    /**
     * 获取任务列表
     */
    @GetMapping
    public Result<List<TaskVO>> getTaskList() {
        try {
            List<TaskVO> tasks = taskService.getTaskList();
            return Result.success("查询成功", tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public Result<TaskVO> getTaskDetail(@PathVariable Long id) {
        try {
            TaskVO task = taskService.getTaskDetail(id);
            return Result.success("查询成功", task);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新任务状态
     */
    @PutMapping("/{id}/status")
    public Result<Object> updateTaskStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        try {
            taskService.updateTaskStatus(id, status);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新任务信息
     */
    @PutMapping("/{id}")
    public Result<Object> updateTask(
            @PathVariable Long id,
            @Valid @RequestBody TaskUpdateDTO taskUpdateDTO) {
        try {
            taskService.updateTask(id, taskUpdateDTO);
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量发布任务
     */
    @PostMapping("/batch")
    public Result<Object> batchCreateTasks(@Valid @RequestBody BatchTaskDTO batchTaskDTO) {
        try {
            taskService.batchCreateTasks(batchTaskDTO);
            return Result.success("批量创建任务成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}

