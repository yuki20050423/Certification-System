package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.swjtu.certification.vo.ReviewRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.util.List;
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
        // 验证任务是否存在
        Task task = taskMapper.selectById(reviewDTO.getTaskId());
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 验证审核员是否有权限审核该任务
        if (!task.getAssessorId().equals(assessorId)) {
            throw new RuntimeException("无权审核该任务");
        }

        // 验证任务状态是否允许保存审核（不允许对待上传状态的任务进行操作）
        if ("PENDING_UPLOAD".equals(task.getStatus())) {
            throw new RuntimeException("待上传状态的任务不允许审核");
        }

        // 保存功能仅用于暂存，不允许保存REJECTED状态
        if ("REJECTED".equals(reviewDTO.getReviewStatus())) {
            throw new RuntimeException("请使用提交审核来完成需修改的审核");
        }

        // 创建审核记录（保存时不发送通知）
        ReviewRecord record = new ReviewRecord();
        record.setTaskId(reviewDTO.getTaskId());
        record.setAssessorId(assessorId);
        record.setReviewStatus(reviewDTO.getReviewStatus());
        record.setScore(reviewDTO.getScore());
        record.setComment(reviewDTO.getComment());
        record.setSuggestions(null); // 保存时不保存修改建议
        record.setReviewTime(LocalDateTime.now());
        reviewRecordMapper.insert(record);

        // 更新任务状态为审核中
        task.setStatus("REVIEWING");
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 创建审核记录
     */
    @Transactional
    public void createReviewRecord(Long assessorId, ReviewDTO reviewDTO) {
        // 验证任务是否存在
        Task task = taskMapper.selectById(reviewDTO.getTaskId());
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        // 验证审核员是否有权限审核该任务
        if (!task.getAssessorId().equals(assessorId)) {
            throw new RuntimeException("无权审核该任务");
        }

        // 验证任务状态是否允许审核（不允许对待上传状态的任务进行操作）
        if ("PENDING_UPLOAD".equals(task.getStatus())) {
            throw new RuntimeException("待上传状态的任务不允许审核");
        }

        // 验证审核状态
        if (!"APPROVED".equals(reviewDTO.getReviewStatus()) && !"REJECTED".equals(reviewDTO.getReviewStatus())) {
            throw new RuntimeException("无效的审核状态");
        }

        // 如果审核状态为REJECTED，修改建议应该填写
        if ("REJECTED".equals(reviewDTO.getReviewStatus()) && 
            (reviewDTO.getSuggestions() == null || reviewDTO.getSuggestions().trim().isEmpty())) {
            throw new RuntimeException("审核拒绝时必须填写修改建议");
        }

        // 创建审核记录
        ReviewRecord record = new ReviewRecord();
        record.setTaskId(reviewDTO.getTaskId());
        record.setAssessorId(assessorId);
        record.setReviewStatus(reviewDTO.getReviewStatus());
        record.setScore(reviewDTO.getScore());
        record.setComment(reviewDTO.getComment());
        record.setSuggestions(reviewDTO.getSuggestions());
        record.setReviewTime(LocalDateTime.now());
        reviewRecordMapper.insert(record);

        // 更新任务状态
        if ("APPROVED".equals(reviewDTO.getReviewStatus())) {
            task.setStatus("APPROVED");
        } else if ("REJECTED".equals(reviewDTO.getReviewStatus())) {
            task.setStatus("NEED_REVISION");
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        // 发送通知给教师
        // 获取课程名称
        String courseName = "未知课程";
        try {
            Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null && teacherEntity.getCourseName() != null) {
                courseName = teacherEntity.getCourseName();
            }
        } catch (Exception e) {
            // 忽略异常，使用默认值
        }

        String notificationTitle;
        String notificationContent;
        if ("APPROVED".equals(reviewDTO.getReviewStatus())) {
            notificationTitle = "审核通过通知";
            notificationContent = String.format("您的任务【%s】已通过审核。", courseName);
            if (reviewDTO.getComment() != null && !reviewDTO.getComment().trim().isEmpty()) {
                notificationContent += "\n审核意见：" + reviewDTO.getComment();
            }
        } else {
            notificationTitle = "审核未通过通知";
            notificationContent = String.format("您的任务【%s】审核未通过，需要修改。", courseName);
            if (reviewDTO.getSuggestions() != null && !reviewDTO.getSuggestions().trim().isEmpty()) {
                notificationContent += "\n修改建议：" + reviewDTO.getSuggestions();
            }
        }

        notificationService.createNotification(
            task.getTeacherId(),
            task.getId(),
            "REVIEW_RESULT",
            notificationTitle,
            notificationContent
        );
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
}

