package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.dto.BatchReviewDTO;
import com.swjtu.certification.dto.ReviewDTO;
import com.swjtu.certification.entity.Course;
import com.swjtu.certification.entity.ReviewRecord;
import com.swjtu.certification.entity.Task;
import com.swjtu.certification.entity.Teacher;
import com.swjtu.certification.entity.User;
import com.swjtu.certification.mapper.CourseMapper;
import com.swjtu.certification.mapper.ReviewRecordMapper;
import com.swjtu.certification.mapper.TeacherMapper;
import com.swjtu.certification.mapper.TaskMapper;
import com.swjtu.certification.mapper.UserMapper;
import com.swjtu.certification.util.TaskItemUtils;
import com.swjtu.certification.vo.ReviewRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 审核记录服务类
 */
@Service
@RequiredArgsConstructor
public class ReviewRecordService {
    private final ReviewRecordMapper reviewRecordMapper;
    private final UserMapper userMapper;
    private final TaskMapper taskMapper;
    private final CourseMapper courseMapper;
    private final TeacherMapper teacherMapper;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ReviewWorkflowService reviewWorkflowService;

    /**
     * 校验任务归属
     */
    public boolean checkTaskOwner(Long taskId, Long teacherId) {
        // 1. 查询任务
         Task task = taskMapper.selectById(taskId);
        // 2. 校验归属
         return task != null && task.getTeacherId().equals(teacherId);
    }

    /**
     * 获取任务审核记录列表
     */
    public List<ReviewRecord> getReviewRecordsByTaskId(Long taskId) {
        // 按时间倒序查询
         return reviewRecordMapper.selectByTaskIdOrderByCreateTimeDesc(taskId);
    }

    /**
     * 获取任务的审核记录列表
     */
    public List<ReviewRecordVO> getTaskReviewRecords(Long taskId) {
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewRecord::getTaskId, taskId);
        wrapper.orderByDesc(ReviewRecord::getReviewTime);

        List<ReviewRecord> records = reviewRecordMapper.selectList(wrapper);
        return records.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 获取最新的审核记录
     */
    public ReviewRecordVO getLatestReviewRecord(Long taskId) {
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewRecord::getTaskId, taskId);
        wrapper.orderByDesc(ReviewRecord::getReviewTime);
        wrapper.last("LIMIT 1");

        ReviewRecord record = reviewRecordMapper.selectOne(wrapper);
        return record != null ? convertToVO(record) : null;
    }

    /**
     * 转换为VO
     */
    private ReviewRecordVO convertToVO(ReviewRecord record) {
        ReviewRecordVO vo = new ReviewRecordVO();
        vo.setId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setAssessorId(record.getAssessorId());
        vo.setItemCode(record.getItemCode());
        vo.setItemName(record.getItemName());
        vo.setReviewStatus(record.getReviewStatus());
        vo.setScore(record.getScore());
        vo.setComment(record.getComment());
        vo.setSuggestions(record.getSuggestions());
        vo.setReviewTime(record.getReviewTime());

        // 设置审核状态描述
        if ("APPROVED".equals(record.getReviewStatus())) {
            vo.setReviewStatusDesc("审核通过");
        } else if ("REJECTED".equals(record.getReviewStatus())) {
            vo.setReviewStatusDesc("需修改");
        } else {
            vo.setReviewStatusDesc("未知");
        }

        // 获取审核员信息
        User assessor = userMapper.selectById(record.getAssessorId());
        if (assessor != null) {
            vo.setAssessorName(assessor.getRealName());
        }

        return vo;
    }

    /**
     * 保存审核（进入审核中状态）
     */
    @Transactional
    public void saveReviewRecord(Long assessorId, ReviewDTO reviewDTO) {
        validateSaveReviewRequest(assessorId, reviewDTO.getTaskId(), reviewDTO.getItemCode(), reviewDTO.getReviewStatus());
        reviewWorkflowService.markAssignmentReviewing(reviewDTO.getTaskId(), assessorId, reviewDTO.getItemCode());
    }

    @Transactional
    public void saveBatchReviewRecord(Long assessorId, BatchReviewDTO batchReviewDTO) {
        Set<String> itemCodes = new LinkedHashSet<>(batchReviewDTO.getItemCodes());
        if (itemCodes.isEmpty()) {
            throw new RuntimeException("请至少选择一个审核项目");
        }
        for (String itemCode : itemCodes) {
            validateSaveReviewRequest(assessorId, batchReviewDTO.getTaskId(), itemCode, batchReviewDTO.getReviewStatus());
            reviewWorkflowService.markAssignmentReviewing(batchReviewDTO.getTaskId(), assessorId, itemCode);
        }
    }

    /**
     * 创建审核记录
     */
    @Transactional
    public void createReviewRecord(Long assessorId, ReviewDTO reviewDTO) {
        Task task = createReviewRecordInternal(assessorId, reviewDTO, false);
        sendTeacherReviewSummary(task, reviewDTO.getReviewStatus(), List.of(reviewDTO.getItemCode()), reviewDTO.getComment(), reviewDTO.getSuggestions(), reviewDTO.getScore());
    }

    @Transactional
    public void createBatchReviewRecord(Long assessorId, BatchReviewDTO batchReviewDTO) {
        Set<String> itemCodes = new LinkedHashSet<>(batchReviewDTO.getItemCodes());
        if (itemCodes.isEmpty()) {
            throw new RuntimeException("请至少选择一个审核项目");
        }

        Task task = null;
        for (String itemCode : itemCodes) {
            ReviewDTO reviewDTO = new ReviewDTO();
            reviewDTO.setTaskId(batchReviewDTO.getTaskId());
            reviewDTO.setItemCode(itemCode);
            reviewDTO.setReviewStatus(batchReviewDTO.getReviewStatus());
            reviewDTO.setScore(batchReviewDTO.getScore());
            reviewDTO.setComment(batchReviewDTO.getComment());
            reviewDTO.setSuggestions(batchReviewDTO.getSuggestions());
            task = createReviewRecordInternal(assessorId, reviewDTO, false);
        }

        if (task != null) {
            sendTeacherReviewSummary(
                    task,
                    batchReviewDTO.getReviewStatus(),
                    new ArrayList<>(itemCodes),
                    batchReviewDTO.getComment(),
                    batchReviewDTO.getSuggestions(),
                    batchReviewDTO.getScore()
            );
        }
    }

    /**
     * 获取审核员的审核记录列表
     */
    public List<ReviewRecordVO> getAssessorReviewRecords(Long assessorId) {
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReviewRecord::getAssessorId, assessorId);
        wrapper.orderByDesc(ReviewRecord::getReviewTime);

        List<ReviewRecord> records = reviewRecordMapper.selectList(wrapper);
        return records.stream().map(record -> convertToVOWithTaskInfo(record)).collect(Collectors.toList());
    }

    /**
     * 转换为VO并包含任务信息
     */
    private ReviewRecordVO convertToVOWithTaskInfo(ReviewRecord record) {
        ReviewRecordVO vo = new ReviewRecordVO();
        vo.setId(record.getId());
        vo.setTaskId(record.getTaskId());
        vo.setAssessorId(record.getAssessorId());
        vo.setItemCode(record.getItemCode());
        vo.setItemName(record.getItemName());
        vo.setReviewStatus(record.getReviewStatus());
        vo.setScore(record.getScore());
        vo.setComment(record.getComment());
        vo.setSuggestions(record.getSuggestions());
        vo.setReviewTime(record.getReviewTime());

        // 设置审核状态描述
        if ("APPROVED".equals(record.getReviewStatus())) {
            vo.setReviewStatusDesc("审核通过");
        } else if ("REJECTED".equals(record.getReviewStatus())) {
            vo.setReviewStatusDesc("需修改");
        } else {
            vo.setReviewStatusDesc("未知");
        }

        // 获取审核员信息
        try {
            User assessor = userMapper.selectById(record.getAssessorId());
            if (assessor != null) {
                vo.setAssessorName(assessor.getRealName());
            }
        } catch (Exception e) {
            System.err.println("获取审核员信息失败: " + e.getMessage());
        }

        // 获取任务信息并关联课程、教师
        try {
            Task task = taskMapper.selectById(record.getTaskId());
            if (task != null) {
                // 从 Teacher 表获取课程名和教学班号（courseId 关联 Teacher 表）
                try {
                    Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
                    if (teacherEntity != null) {
                        vo.setCourseName(teacherEntity.getCourseName());
                        vo.setTeachingClass(teacherEntity.getTeachingClass());
                    } else {
                        vo.setCourseName("未知课程");
                        vo.setTeachingClass("");
                    }
                } catch (Exception e) {
                    System.err.println("获取课程信息失败: " + e.getMessage());
                    vo.setCourseName("未知课程");
                    vo.setTeachingClass("");
                }

                // 获取教师信息（teacherId 关联 User 表）
                try {
                    User teacher = userMapper.selectById(task.getTeacherId());
                    if (teacher != null) {
                        vo.setTeacherName(teacher.getRealName());
                    } else {
                        vo.setTeacherName("未知教师");
                    }
                } catch (Exception e) {
                    System.err.println("获取教师信息失败: " + e.getMessage());
                    vo.setTeacherName("未知教师");
                }
            } else {
                vo.setCourseName("任务已删除");
                vo.setTeacherName("-");
                vo.setTeachingClass("");
            }
        } catch (Exception e) {
            System.err.println("获取任务信息失败: " + e.getMessage());
            vo.setCourseName("未知");
            vo.setTeacherName("未知");
            vo.setTeachingClass("");
        }

        return vo;
    }

    /**
     * 获取教师的所有审核记录（包含课程信息）
     */
    public List<ReviewRecordVO> getTeacherReviewRecords(Long teacherId) {
        // 1. 查询该教师的所有任务ID
        LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(Task::getTeacherId, teacherId);
        List<Task> tasks = taskMapper.selectList(taskWrapper);
        if (tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());

        // 2. 查询这些任务的审核记录
        LambdaQueryWrapper<ReviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ReviewRecord::getTaskId, taskIds);
        wrapper.orderByDesc(ReviewRecord::getReviewTime);
        List<ReviewRecord> records = reviewRecordMapper.selectList(wrapper);

        // 3. 转换为VO并填充课程信息（复用审核员的 convertToVOWithTaskInfo 方法）
        return records.stream().map(this::convertToVOWithTaskInfo).collect(Collectors.toList());
    }

    private String buildApprovedEmailContent(String courseName, String itemName, String comment, BigDecimal score) {
        String scoreStr = score != null ? score.toString() : "未评分";
        String commentStr = (comment != null && !comment.isEmpty()) ? comment : "无";
        return String.format(
                "<h3>✅ 专业认证年度备案 - 审核通过</h3>" +
                        "<p>您的备案任务 <strong>%s</strong> 中的项目 <strong>%s</strong> 已通过审核。</p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>评分：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>审核意见：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://localhost:8080/teacher/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>查看详情</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, itemName, scoreStr, commentStr
        );
    }

    private String buildRejectedEmailContent(String courseName, String itemName, String suggestions) {
        String sugStr = (suggestions != null && !suggestions.isEmpty()) ? suggestions : "请根据反馈修改后重新提交";
        return String.format(
                "<h3>🔄 专业认证年度备案 - 需修改</h3>" +
                        "<p>您的备案任务 <strong>%s</strong> 中的项目 <strong>%s</strong> 需要修改后重新提交。</p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>修改建议：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://localhost:8080/teacher/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>去修改</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, itemName, sugStr
        );
    }

    private void validateSaveReviewRequest(Long assessorId, Long taskId, String itemCode, String reviewStatus) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!reviewWorkflowService.canAssessorReviewTask(taskId, assessorId)) {
            throw new RuntimeException("无权审核该任务");
        }
        if ("PENDING_UPLOAD".equals(task.getStatus())) {
            throw new RuntimeException("待上传状态的任务不允许审核");
        }
        if ("REJECTED".equals(reviewStatus)) {
            throw new RuntimeException("请使用提交审核来完成需修改的审核");
        }
        reviewWorkflowService.getActiveAssignment(taskId, assessorId, itemCode);
    }

    private Task createReviewRecordInternal(Long assessorId, ReviewDTO reviewDTO, boolean sendTeacherNotification) {
        Task task = taskMapper.selectById(reviewDTO.getTaskId());
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        if (!reviewWorkflowService.canAssessorReviewTask(reviewDTO.getTaskId(), assessorId)) {
            throw new RuntimeException("无权审核该任务");
        }
        if ("PENDING_UPLOAD".equals(task.getStatus())) {
            throw new RuntimeException("待上传状态的任务不允许审核");
        }
        if (!"APPROVED".equals(reviewDTO.getReviewStatus()) && !"REJECTED".equals(reviewDTO.getReviewStatus())) {
            throw new RuntimeException("无效的审核状态");
        }
        if ("REJECTED".equals(reviewDTO.getReviewStatus()) &&
                (reviewDTO.getSuggestions() == null || reviewDTO.getSuggestions().trim().isEmpty())) {
            throw new RuntimeException("审核拒绝时必须填写修改建议");
        }

        ReviewRecord record = new ReviewRecord();
        record.setTaskId(reviewDTO.getTaskId());
        record.setAssessorId(assessorId);
        record.setItemCode(reviewDTO.getItemCode());
        record.setItemName(TaskItemUtils.getItemName(reviewDTO.getItemCode()));
        record.setReviewStatus(reviewDTO.getReviewStatus());
        record.setScore(reviewDTO.getScore());
        record.setComment(reviewDTO.getComment());
        record.setSuggestions(reviewDTO.getSuggestions());
        record.setReviewTime(LocalDateTime.now());
        reviewRecordMapper.insert(record);

        reviewWorkflowService.completeAssignment(
                reviewDTO.getTaskId(),
                assessorId,
                reviewDTO.getItemCode(),
                reviewDTO.getReviewStatus()
        );

        if (sendTeacherNotification) {
            sendTeacherReviewSummary(task, reviewDTO.getReviewStatus(), List.of(reviewDTO.getItemCode()), reviewDTO.getComment(), reviewDTO.getSuggestions(), reviewDTO.getScore());
        }
        return task;
    }

    private void sendTeacherReviewSummary(Task task, String reviewStatus, List<String> itemCodes, String comment, String suggestions, BigDecimal score) {
        String courseName = "未知课程";
        try {
            Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null && teacherEntity.getCourseName() != null) {
                courseName = teacherEntity.getCourseName();
            }
        } catch (Exception ignored) {
        }

        List<String> itemDisplays = itemCodes.stream()
                .map(code -> {
                    String name = TaskItemUtils.getItemName(code);
                    return name != null ? name : code;
                })
                .distinct()
                .collect(Collectors.toList());
        String itemsText = String.join("、", itemDisplays);

        String notificationTitle;
        String notificationContent;
        if ("APPROVED".equals(reviewStatus)) {
            notificationTitle = itemCodes.size() > 1 ? "批量审核通过通知" : "审核通过通知";
            notificationContent = String.format("您的任务【%s】中的项目【%s】已通过审核。", courseName, itemsText);
            if (comment != null && !comment.trim().isEmpty()) {
                notificationContent += "\n审核意见：" + comment;
            }
        } else {
            notificationTitle = itemCodes.size() > 1 ? "批量审核未通过通知" : "审核未通过通知";
            notificationContent = String.format("您的任务【%s】中的项目【%s】审核未通过，需要修改。", courseName, itemsText);
            if (suggestions != null && !suggestions.trim().isEmpty()) {
                notificationContent += "\n修改建议：" + suggestions;
            }
        }

        notificationService.createNotification(
                task.getTeacherId(),
                task.getId(),
                "REVIEW_RESULT",
                notificationTitle,
                notificationContent
        );

        User teacher = userMapper.selectById(task.getTeacherId());
        if (teacher != null && teacher.getEmail() != null && !teacher.getEmail().isEmpty()) {
            String subject;
            String content;
            if ("APPROVED".equals(reviewStatus)) {
                subject = itemCodes.size() > 1
                        ? "【专业认证备案】批量审核通过 - " + courseName
                        : "【专业认证备案】审核通过 - " + courseName + " - " + itemsText;
                content = buildApprovedBatchEmailContent(courseName, itemsText, comment, score);
            } else {
                subject = itemCodes.size() > 1
                        ? "【专业认证备案】批量需修改 - " + courseName
                        : "【专业认证备案】需修改 - " + courseName + " - " + itemsText;
                content = buildRejectedBatchEmailContent(courseName, itemsText, suggestions);
            }
            emailService.sendHtmlMail(teacher.getEmail(), subject, content);
        }
    }

    private String buildApprovedBatchEmailContent(String courseName, String itemsText, String comment, BigDecimal score) {
        String scoreStr = score != null ? score.toString() : "未评分";
        String commentStr = (comment != null && !comment.isEmpty()) ? comment : "无";
        return String.format(
                "<h3>✅ 专业认证年度备案 - 审核通过</h3>" +
                        "<p>您的备案任务 <strong>%s</strong> 中以下项目已通过审核：</p>" +
                        "<p><strong>%s</strong></p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>评分：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>审核意见：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://localhost:8080/teacher/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>查看详情</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, itemsText, scoreStr, commentStr
        );
    }

    private String buildRejectedBatchEmailContent(String courseName, String itemsText, String suggestions) {
        String sugStr = (suggestions != null && !suggestions.isEmpty()) ? suggestions : "请根据反馈修改后重新提交";
        return String.format(
                "<h3>🔄 专业认证年度备案 - 需修改</h3>" +
                        "<p>您的备案任务 <strong>%s</strong> 中以下项目需要修改后重新提交：</p>" +
                        "<p><strong>%s</strong></p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>修改建议：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://localhost:8080/teacher/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>去修改</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, itemsText, sugStr
        );
    }
}

