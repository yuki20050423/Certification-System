package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.entity.Notification;
import com.swjtu.certification.entity.User;
import com.swjtu.certification.mapper.NotificationMapper;
import com.swjtu.certification.mapper.UserMapper;
import com.swjtu.certification.vo.NotificationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知服务类
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;

    /**
     * 创建通知
     */
    @Transactional
    public void createNotification(Long userId, Long taskId, String type, String title, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTaskId(taskId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setIsRead(0);
        notification.setCreateTime(LocalDateTime.now());
        notificationMapper.insert(notification);
    }

    /**
     * 获取用户的通知列表
     */
    public List<NotificationVO> getUserNotifications(Long userId, Boolean unreadOnly) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        if (unreadOnly != null && unreadOnly) {
            wrapper.eq(Notification::getIsRead, 0);
        }
        wrapper.orderByDesc(Notification::getCreateTime);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        return notifications.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 根据任务ID获取通知列表
     */
    public List<NotificationVO> getNotificationsByTaskId(Long taskId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getTaskId, taskId);
        wrapper.orderByDesc(Notification::getCreateTime);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        return notifications.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 根据用户ID获取通知列表
     */
    public List<NotificationVO> getNotificationsByUserId(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.orderByDesc(Notification::getCreateTime);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        return notifications.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 标记通知为已读
     */
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification != null && notification.getUserId().equals(userId)) {
            notification.setIsRead(1);
            notificationMapper.updateById(notification);
        }
    }

    /**
     * 标记所有通知为已读
     */
    @Transactional
    public void markAllAsRead(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.eq(Notification::getIsRead, 0);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        for (Notification notification : notifications) {
            notification.setIsRead(1);
            notificationMapper.updateById(notification);
        }
    }

    /**
     * 获取未读通知数量
     */
    public Long getUnreadCount(Long userId) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        wrapper.eq(Notification::getIsRead, 0);
        return notificationMapper.selectCount(wrapper);
    }

    /**
     * 转换为VO
     */
    private NotificationVO convertToVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setUserId(notification.getUserId());
        vo.setTaskId(notification.getTaskId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setIsRead(notification.getIsRead());
        vo.setCreateTime(notification.getCreateTime());

        // 获取用户信息
        User user = userMapper.selectById(notification.getUserId());
        if (user != null) {
            vo.setUserName(user.getRealName());
            vo.setUserRole(user.getRole());
        }

        // 设置通知类型描述
        switch (notification.getType()) {
            case "NEW_TASK":
                vo.setTypeDesc("新任务");
                break;
            case "REVIEW_RESULT":
                vo.setTypeDesc("审核结果");
                break;
            case "TASK_REMINDER":
                vo.setTypeDesc("任务提醒");
                break;
            case "TASK_SUBMITTED":   // 新增
                vo.setTypeDesc("任务待审核");
                break;
            default:
                vo.setTypeDesc("系统通知");
        }

        return vo;
    }

    /**
     * 编辑通知
     */
    @Transactional
    public void updateNotification(Long notificationId, Long userId, String title, String content) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }
        notification.setTitle(title);
        notification.setContent(content);
        notificationMapper.updateById(notification);
    }

    /**
     * 删除通知
     */
    @Transactional
    public void deleteNotification(Long notificationId, Long userId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new RuntimeException("通知不存在");
        }
        notificationMapper.deleteById(notificationId);
    }

    /**
     * 获取所有通知（管理员）
     */
    public List<NotificationVO> getAllNotifications() {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Notification::getCreateTime);

        List<Notification> notifications = notificationMapper.selectList(wrapper);
        return notifications.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 校验通知归属
     */
    public boolean checkNotificationOwner(Long notificationId, Long userId) {
        // 1. 查询通知
         Notification notification = notificationMapper.selectById(notificationId);
        // 2. 校验归属
         return notification != null && notification.getUserId().equals(userId);
    }
}

