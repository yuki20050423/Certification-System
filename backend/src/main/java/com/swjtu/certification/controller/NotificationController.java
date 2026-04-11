package com.swjtu.certification.controller;

import com.swjtu.certification.service.NotificationService;
import com.swjtu.certification.vo.NotificationVO;
import com.swjtu.certification.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 通知控制器
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {
    @Autowired
    private NotificationService notificationService;

    /**
     * 获取用户的通知列表（教师端）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserNotifications(
            @RequestParam("teacherId") Long userId) {   // ✅ 从参数接收，不再使用 session
        Map<String, Object> result = new HashMap<>();
        try {
            System.out.println("=== getUserNotifications called, userId=" + userId); // 调试日志
            List<NotificationVO> notificationList = notificationService.getNotificationsByUserId(userId);
            result.put("code", 200);
            result.put("msg", "获取通知列表成功");
            result.put("data", notificationList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "获取通知列表失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 根据任务ID获取通知列表（其他接口保持不变）
     */
    @GetMapping("/task/{taskId}")
    public Result<List<NotificationVO>> getNotificationsByTaskId(@PathVariable Long taskId) {
        try {
            List<NotificationVO> notifications = notificationService.getNotificationsByTaskId(taskId);
            return Result.success("查询成功", notifications);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 标记通知为已读（教师端）
     */
    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable Long notificationId,
            @RequestParam("teacherId") Long userId) {   // ✅ 从参数接收
        Map<String, Object> result = new HashMap<>();
        try {
            // 校验通知归属（service 中已包含校验）
            notificationService.markAsRead(notificationId, userId);
            result.put("code", 200);
            result.put("msg", "标记已读成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "标记已读失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 标记所有通知为已读（教师端）
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @RequestParam("teacherId") Long userId) {   // ✅ 从参数接收
        Map<String, Object> result = new HashMap<>();
        try {
            notificationService.markAllAsRead(userId);
            result.put("code", 200);
            result.put("msg", "全部标记已读成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "全部标记已读失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 获取未读通知数量（教师端）
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @RequestParam("teacherId") Long userId) {   // ✅ 从参数接收
        Map<String, Object> result = new HashMap<>();
        try {
            long unreadCount = notificationService.getUnreadCount(userId);
            result.put("code", 200);
            result.put("msg", "获取未读数量成功");
            result.put("data", unreadCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取未读数量失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 编辑通知
     */
    @PutMapping("/{notificationId}")
    public Result<Object> updateNotification(
            @PathVariable Long notificationId,
            @RequestParam Long userId,
            @RequestParam String title,
            @RequestParam String content) {
        try {
            notificationService.updateNotification(notificationId, userId, title, content);
            return Result.success("编辑成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{notificationId}")
    public Result<Object> deleteNotification(
            @PathVariable Long notificationId,
            @RequestParam Long userId) {
        try {
            notificationService.deleteNotification(notificationId, userId);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取所有通知（管理员）
     */
    @GetMapping("/all")
    public Result<List<NotificationVO>> getAllNotifications() {
        try {
            List<NotificationVO> notifications = notificationService.getAllNotifications();
            return Result.success("查询成功", notifications);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}

