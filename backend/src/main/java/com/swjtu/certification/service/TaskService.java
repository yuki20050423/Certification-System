package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.dto.BatchTaskDTO;
import com.swjtu.certification.dto.TaskUpdateDTO;
import com.swjtu.certification.entity.Task;
import com.swjtu.certification.entity.Teacher;
import com.swjtu.certification.entity.User;
import com.swjtu.certification.entity.File;
import com.swjtu.certification.entity.Course;
import com.swjtu.certification.mapper.CourseMapper;
import com.swjtu.certification.mapper.TaskMapper;
import com.swjtu.certification.mapper.TeacherMapper;
import com.swjtu.certification.mapper.UserMapper;
import com.swjtu.certification.mapper.FileMapper;
import com.swjtu.certification.vo.TeacherTaskStatisticsVO;
import com.swjtu.certification.vo.AssessorTaskVO;
import com.swjtu.certification.vo.ReviewStatisticsVO;
import com.swjtu.certification.vo.TaskVO;
import com.swjtu.certification.vo.TeacherTaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * 任务服务类
 */
@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final FileMapper fileMapper;
    private final NotificationService notificationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();


    /**
     * 获取任务列表
     */
    public List<TaskVO> getTaskList() {
        List<Task> tasks = taskMapper.selectList(null);
        return tasks.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 获取任务详情
     */
    public TaskVO getTaskDetail(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        return convertToVO(task);
    }

    /**
     * 更新任务状态
     */
    @Transactional
    public void updateTaskStatus(Long id, String status) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        task.setStatus(status);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 更新任务信息
     */
    @Transactional
    public void updateTask(Long id, TaskUpdateDTO taskUpdateDTO) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        task.setDescription(taskUpdateDTO.getDescription());
        task.setMaterialRequirements(taskUpdateDTO.getMaterialRequirements());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /**
     * 批量创建任务
     */
    @Transactional
    public void batchCreateTasks(BatchTaskDTO batchTaskDTO) {
        System.out.println("=== 开始批量创建任务 ===");
        System.out.println("任务数量: " + batchTaskDTO.getTasks().size());

        for (BatchTaskDTO.TaskItem item : batchTaskDTO.getTasks()) {
            System.out.println("处理任务 - 课程ID: " + item.getCourseId() + ", 教师ID: " + item.getTeacherId());

            // 获取课程信息用于创建通知
            Teacher teacherEntity = teacherMapper.selectById(item.getCourseId());
            if (teacherEntity == null) {
                throw new RuntimeException("课程信息不存在，ID: " + item.getCourseId());
            }

            // 直接从user表获取教师信息
            System.out.println("查询user表，teacherId: " + item.getTeacherId());
            User teacher = userMapper.selectById(item.getTeacherId());

            if (teacher == null) {
                System.out.println("user表中找不到教师，teacherId: " + item.getTeacherId());
                LambdaQueryWrapper<User> allUserWrapper = new LambdaQueryWrapper<>();
                allUserWrapper.eq(User::getRole, "TEACHER");
                allUserWrapper.last("LIMIT 10");
                List<User> allUsers = userMapper.selectList(allUserWrapper);
                System.out.println("user表中的教师记录（前10条）:");
                allUsers.forEach(u -> System.out.println("  ID: " + u.getId() + ", 姓名: " + u.getRealName()));
                throw new RuntimeException("教师不存在，ID: " + item.getTeacherId());
            }

            System.out.println("找到教师: " + teacher.getRealName() + ", user.id: " + teacher.getId());

            // 审核员可空，仅当存在时进行校验
            if (item.getAssessorId() != null) {
                User assessor = userMapper.selectById(item.getAssessorId());
                if (assessor == null) {
                    throw new RuntimeException("审核员不存在，ID: " + item.getAssessorId());
                }
                if (teacher.getId().equals(item.getAssessorId())) {
                    throw new RuntimeException("审核员不能是教师本人");
                }
            }

            // 检查课程和教师是否已被指派（是否有未完成的任务）
            LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
            taskWrapper.eq(Task::getCourseId, item.getCourseId());
            taskWrapper.eq(Task::getTeacherId, teacher.getId());
            taskWrapper.and(w -> w.eq(Task::getStatus, "PENDING_UPLOAD")
                    .or().eq(Task::getStatus, "SUBMITTED")
                    .or().eq(Task::getStatus, "REVIEWING")
                    .or().eq(Task::getStatus, "NEED_REVISION"));
            Long existingTaskCount = taskMapper.selectCount(taskWrapper);

            if (existingTaskCount > 0) {
                // 查询并显示现有的任务详情
                LambdaQueryWrapper<Task> detailWrapper = new LambdaQueryWrapper<>();
                detailWrapper.eq(Task::getCourseId, item.getCourseId());
                detailWrapper.eq(Task::getTeacherId, teacher.getId());
                detailWrapper.and(w -> w.eq(Task::getStatus, "PENDING_UPLOAD")
                        .or().eq(Task::getStatus, "SUBMITTED")
                        .or().eq(Task::getStatus, "REVIEWING")
                        .or().eq(Task::getStatus, "NEED_REVISION"));
                List<Task> existingTasks = taskMapper.selectList(detailWrapper);
                System.out.println("发现已存在的任务:");
                existingTasks.forEach(t -> {
                    System.out.println("  任务ID: " + t.getId() +
                            ", 课程ID: " + t.getCourseId() +
                            ", 教师ID: " + t.getTeacherId() +
                            ", 状态: " + t.getStatus());
                });
                throw new RuntimeException("该教师已被指派此课程，课程ID: " + item.getCourseId() + "，教师ID: " + teacher.getId());
            }

            // 创建任务
            Task task = new Task();
            task.setCourseId(item.getCourseId());
            task.setTeacherId(teacher.getId());
            task.setAssessorId(item.getAssessorId());   // 允许 null
            task.setDeadline(item.getDeadline());
            task.setStatus("PENDING_UPLOAD");
            task.setCreateTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.insert(task);

            // 为教师创建新任务通知
            String teacherNotificationTitle = "新任务通知";
            String teacherNotificationContent = String.format(
                    "您收到了一个新的备案任务。课程：%s，教学班：%s，截止日期：%s。请及时完成材料上传。",
                    teacherEntity.getCourseName(),
                    teacherEntity.getTeachingClass(),
                    item.getDeadline().toString()
            );
            notificationService.createNotification(
                    teacher.getId(),
                    task.getId(),
                    "NEW_TASK",
                    teacherNotificationTitle,
                    teacherNotificationContent
            );

            // 为审核员创建新任务通知（仅当有审核员时）
            if (item.getAssessorId() != null) {
                String assessorNotificationTitle = "新审核任务通知";
                String assessorNotificationContent = String.format(
                        "您收到了一个新的审核任务。课程：%s，教学班：%s，教师：%s，截止日期：%s。请及时进行审核。",
                        teacherEntity.getCourseName(),
                        teacherEntity.getTeachingClass(),
                        teacher.getRealName(),
                        item.getDeadline().toString()
                );
                notificationService.createNotification(
                        item.getAssessorId(),
                        task.getId(),
                        "NEW_TASK",
                        assessorNotificationTitle,
                        assessorNotificationContent
                );
            }
        }
    }

    /**
     * 转换为VO
     */
    private TaskVO convertToVO(Task task) {
        TaskVO vo = new TaskVO();
        vo.setId(task.getId());
        vo.setCourseId(task.getCourseId());
        vo.setTeacherId(task.getTeacherId());
        vo.setAssessorId(task.getAssessorId());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setCreateTime(task.getCreateTime());
        vo.setUpdateTime(task.getUpdateTime());
        vo.setDescription(task.getDescription());
        vo.setMaterialRequirements(task.getMaterialRequirements());

        switch (task.getStatus()) {
            case "PENDING_UPLOAD": vo.setStatusDesc("待上传"); break;
            case "SUBMITTED": vo.setStatusDesc("已提交"); break;
            case "PENDING_REVIEW": vo.setStatusDesc("审核中"); break;
            case "REVIEWING": vo.setStatusDesc("审核中"); break;
            case "APPROVED": vo.setStatusDesc("审核通过"); break;
            case "NEED_REVISION": vo.setStatusDesc("需修改"); break;
            default: vo.setStatusDesc("未知");
        }

        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null) {
                User taskTeacher = userMapper.selectById(task.getTeacherId());
                if (taskTeacher != null && !teacherEntity.getTeacherName().equals(taskTeacher.getRealName())) {
                    teacherEntity = null;
                }
            }
        }

        if (teacherEntity != null) {
            vo.setCourseName(teacherEntity.getCourseName());
            vo.setTeachingClass(teacherEntity.getTeachingClass());
            if (teacherEntity.getPreferred() != null) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{4}");
                java.util.regex.Matcher matcher = pattern.matcher(teacherEntity.getPreferred());
                if (matcher.find()) {
                    vo.setGrade(matcher.group());
                }
            }
            vo.setSemester(teacherEntity.getSemester());
        } else {
            Course course = courseMapper.selectById(task.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getCourseName());
                vo.setTeachingClass("未开课");
                vo.setGrade(course.getGrade());
                vo.setSemester(course.getSemester());
            } else {
                vo.setCourseName("未知课程");
                vo.setTeachingClass("");
            }
        }

        if (task.getTeacherId() != null) {
            User teacher = userMapper.selectById(task.getTeacherId());
            if (teacher != null) {
                vo.setTeacherWorkId(teacher.getWorkId());
                vo.setTeacherName(teacher.getRealName());
            }
        } else {
            vo.setTeacherName("未分配教师");
        }

        if (task.getAssessorId() != null) {
            User assessor = userMapper.selectById(task.getAssessorId());
            if (assessor != null) {
                vo.setAssessorWorkId(assessor.getWorkId());
                vo.setAssessorName(assessor.getRealName());
            }
        }

        return vo;
    }

    /**
     * 获取教师的任务列表
     */
    public List<TeacherTaskVO> getTeacherTasks(Long teacherId, String status, String sortBy, String order) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getTeacherId, teacherId);

        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Task::getStatus, status);
        }

        // 根据 sortBy 和 order 动态排序
        if ("deadline".equals(sortBy)) {
            wrapper.orderBy(true, "asc".equalsIgnoreCase(order), Task::getDeadline);
        } else if ("createTime".equals(sortBy)) {
            wrapper.orderBy(true, "asc".equalsIgnoreCase(order), Task::getCreateTime);
        } else {
            wrapper.orderByDesc(Task::getCreateTime); // 默认
        }

        List<Task> tasks = taskMapper.selectList(wrapper);
        return tasks.stream().map(this::convertToTeacherTaskVO).collect(Collectors.toList());
    }

    /**
     * 获取教师的任务详情
     */
    public TeacherTaskVO getTeacherTaskDetail(Long taskId, Long teacherId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getTeacherId().equals(teacherId)) {
            throw new RuntimeException("无权查看该任务");
        }
        return convertToTeacherTaskVO(task);
    }

    /**
     * 提交任务（将状态改为已提交）
     */
    @Transactional
    public void submitTask(Long taskId, Long teacherId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getTeacherId().equals(teacherId)) {
            throw new RuntimeException("无权提交该任务");
        }
        // 检查截止日期
        if (LocalDateTime.now().isAfter(task.getDeadline())) {
            throw new RuntimeException("任务已截止，无法提交");
        }
        // 检查文件数量
        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getTaskId, taskId).eq(File::getStatus, "UPLOADED");
        long fileCount = fileMapper.selectCount(fileWrapper);
        if (fileCount == 0) {
            throw new RuntimeException("请先上传文件");
        }
        // 检查状态
        if (!"PENDING_UPLOAD".equals(task.getStatus()) && !"NEED_REVISION".equals(task.getStatus())) {
            throw new RuntimeException("当前任务状态不允许提交");
        }

        task.setStatus("SUBMITTED");
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);

        // ========== 给审核员发送通知 ==========
        Long assessorId = task.getAssessorId();
        if (assessorId != null) {
            // 获取教师姓名
            User teacher = userMapper.selectById(teacherId);
            // 获取课程信息
            Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacher != null && teacherEntity != null) {
                String teacherName = teacher.getRealName();
                String courseName = teacherEntity.getCourseName();
                String teachingClass = teacherEntity.getTeachingClass();

                String title = "任务待审核通知";
                String content = String.format("教师 %s 已提交课程 %s（教学班 %s）的备案材料，请及时审核。",
                        teacherName, courseName, teachingClass);

                notificationService.createNotification(assessorId, taskId, "TASK_SUBMITTED", title, content);
            }
        }
    }

    /**
     * 转换为教师任务VO
     */
    private TeacherTaskVO convertToTeacherTaskVO(Task task) {
        TeacherTaskVO vo = new TeacherTaskVO();
        vo.setId(task.getId());
        vo.setCourseId(task.getCourseId());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setDescription(task.getDescription());
        vo.setMaterialRequirements(task.getMaterialRequirements());
        vo.setCreateTime(task.getCreateTime());

        switch (task.getStatus()) {
            case "PENDING_UPLOAD": vo.setStatusDesc("待上传"); break;
            case "SUBMITTED": vo.setStatusDesc("已提交"); break;
            case "PENDING_REVIEW": vo.setStatusDesc("审核中"); break;
            case "REVIEWING": vo.setStatusDesc("审核中"); break;
            case "APPROVED": vo.setStatusDesc("审核通过"); break;
            case "NEED_REVISION": vo.setStatusDesc("需修改"); break;
            default: vo.setStatusDesc("未知");
        }

        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null) {
                User taskTeacher = userMapper.selectById(task.getTeacherId());
                if (taskTeacher != null && !teacherEntity.getTeacherName().equals(taskTeacher.getRealName())) {
                    teacherEntity = null;
                }
            }
        }

        if (teacherEntity != null) {
            vo.setCourseName(teacherEntity.getCourseName());
            vo.setTeachingClass(teacherEntity.getTeachingClass());
            if (teacherEntity.getPreferred() != null) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{4}");
                java.util.regex.Matcher matcher = pattern.matcher(teacherEntity.getPreferred());
                if (matcher.find()) {
                    vo.setGrade(matcher.group());
                }
            }
            vo.setSemester(teacherEntity.getSemester());
        } else {
            Course course = courseMapper.selectById(task.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getCourseName());
                vo.setTeachingClass("未开课");
                vo.setGrade(course.getGrade());
                vo.setSemester(course.getSemester());
            } else {
                vo.setCourseName("未知课程");
                vo.setTeachingClass("");
            }
        }

        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getTaskId, task.getId());
        fileWrapper.eq(File::getStatus, "UPLOADED");
        Long fileCount = fileMapper.selectCount(fileWrapper);
        vo.setFileCount(fileCount.intValue());

        vo.setIsExpired(LocalDateTime.now().isAfter(task.getDeadline()));

        return vo;
    }

    /**
     * 获取审核员的任务列表
     */
    public List<AssessorTaskVO> getAssessorTasks(Long assessorId, String status, Long teacherId) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getAssessorId, assessorId);

        // 按状态筛选
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Task::getStatus, status);
        }

        // 按教师筛选
        if (teacherId != null) {
            wrapper.eq(Task::getTeacherId, teacherId);
        }

        wrapper.orderByDesc(Task::getCreateTime);

        List<Task> tasks = taskMapper.selectList(wrapper);
        return tasks.stream().map(task -> convertToAssessorTaskVO(task)).collect(Collectors.toList());
    }

    /**
     * 获取审核员的任务详情
     */
    public AssessorTaskVO getAssessorTaskDetail(Long taskId, Long assessorId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!task.getAssessorId().equals(assessorId)) {
            throw new RuntimeException("无权查看该任务");
        }
        return convertToAssessorTaskVO(task);
    }

    /**
     * 获取审核统计信息
     */
    public ReviewStatisticsVO getReviewStatistics(Long assessorId) {
        ReviewStatisticsVO statistics = new ReviewStatisticsVO();

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getAssessorId, assessorId);

        // 总任务数
        statistics.setTotalTasks(taskMapper.selectCount(wrapper));

        // 待审核任务数（已提交）
        LambdaQueryWrapper<Task> submittedWrapper = new LambdaQueryWrapper<>();
        submittedWrapper.eq(Task::getAssessorId, assessorId);
        submittedWrapper.eq(Task::getStatus, "SUBMITTED");
        statistics.setPendingTasks(taskMapper.selectCount(submittedWrapper));

        // 审核中任务数
        LambdaQueryWrapper<Task> reviewingWrapper = new LambdaQueryWrapper<>();
        reviewingWrapper.eq(Task::getAssessorId, assessorId);
        reviewingWrapper.eq(Task::getStatus, "REVIEWING");
        statistics.setReviewingTasks(taskMapper.selectCount(reviewingWrapper));

        // 审核通过任务数
        LambdaQueryWrapper<Task> approvedWrapper = new LambdaQueryWrapper<>();
        approvedWrapper.eq(Task::getAssessorId, assessorId);
        approvedWrapper.eq(Task::getStatus, "APPROVED");
        statistics.setApprovedTasks(taskMapper.selectCount(approvedWrapper));

        // 需修改任务数
        LambdaQueryWrapper<Task> needRevisionWrapper = new LambdaQueryWrapper<>();
        needRevisionWrapper.eq(Task::getAssessorId, assessorId);
        needRevisionWrapper.eq(Task::getStatus, "NEED_REVISION");
        statistics.setNeedRevisionTasks(taskMapper.selectCount(needRevisionWrapper));

        // 已处理任务数（审核通过 + 需修改）
        Long processedTasks = statistics.getApprovedTasks() + statistics.getNeedRevisionTasks();
        statistics.setProcessedTasks(processedTasks);

        // 计算通过率
        if (processedTasks > 0) {
            double approvalRate = (statistics.getApprovedTasks().doubleValue() / processedTasks.doubleValue()) * 100;
            statistics.setApprovalRate(Math.round(approvalRate * 100.0) / 100.0);
        } else {
            statistics.setApprovalRate(0.0);
        }

        return statistics;
    }

    /**
     * 转换为审核员任务VO
     */
    private AssessorTaskVO convertToAssessorTaskVO(Task task) {
        AssessorTaskVO vo = new AssessorTaskVO();
        vo.setId(task.getId());
        vo.setCourseId(task.getCourseId());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setDescription(task.getDescription());
        vo.setMaterialRequirements(task.getMaterialRequirements());
        vo.setCreateTime(task.getCreateTime());

        switch (task.getStatus()) {
            case "PENDING_UPLOAD": vo.setStatusDesc("待上传"); break;
            case "SUBMITTED": vo.setStatusDesc("待审核"); break;
            case "REVIEWING": vo.setStatusDesc("审核中"); break;
            case "APPROVED": vo.setStatusDesc("审核通过"); break;
            case "NEED_REVISION": vo.setStatusDesc("需修改"); break;
            default: vo.setStatusDesc("未知");
        }

        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null) {
                User taskTeacher = userMapper.selectById(task.getTeacherId());
                if (taskTeacher != null && !teacherEntity.getTeacherName().equals(taskTeacher.getRealName())) {
                    teacherEntity = null;
                }
            }
        }

        if (teacherEntity != null) {
            vo.setCourseName(teacherEntity.getCourseName());
            vo.setTeachingClass(teacherEntity.getTeachingClass());
            if (teacherEntity.getPreferred() != null) {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{4}");
                java.util.regex.Matcher matcher = pattern.matcher(teacherEntity.getPreferred());
                if (matcher.find()) {
                    vo.setGrade(matcher.group());
                }
            }
            vo.setSemester(teacherEntity.getSemester());
        } else {
            Course course = courseMapper.selectById(task.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getCourseName());
                vo.setTeachingClass("未开课");
                vo.setGrade(course.getGrade());
                vo.setSemester(course.getSemester());
            } else {
                vo.setCourseName("未知课程");
                vo.setTeachingClass("");
            }
        }

        if (task.getTeacherId() != null) {
            User teacher = userMapper.selectById(task.getTeacherId());
            if (teacher != null) {
                vo.setTeacherId(teacher.getId());
                vo.setTeacherWorkId(teacher.getWorkId());
                vo.setTeacherName(teacher.getRealName());
            }
        } else {
            vo.setTeacherName("未分配教师");
        }

        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getTaskId, task.getId());
        fileWrapper.eq(File::getStatus, "UPLOADED");
        Long fileCount = fileMapper.selectCount(fileWrapper);
        vo.setFileCount(fileCount.intValue());

        if ("SUBMITTED".equals(task.getStatus()) || "REVIEWING".equals(task.getStatus()) ||
                "APPROVED".equals(task.getStatus()) || "NEED_REVISION".equals(task.getStatus())) {
            vo.setSubmitTime(task.getUpdateTime());
        }

        return vo;
    }

    private String generateWorkId(String realName) {
        String pinyin = convertToPinyin(realName);
        if (pinyin == null || pinyin.isEmpty()) {
            return String.valueOf(System.currentTimeMillis());
        }
        return pinyin.toUpperCase();
    }

    private String convertToPinyin(String chinese) {
        if (chinese == null || chinese.isEmpty()) {
            return "";
        }
        
        StringBuilder pinyin = new StringBuilder();
        for (char c : chinese.toCharArray()) {
            if (c >= 0x4e00 && c <= 0x9fa5) {
                pinyin.append(c);
            } else {
                pinyin.append(c);
            }
        }
        
        return pinyin.length() > 0 ? pinyin.substring(0, Math.min(6, pinyin.length())) : "TEACHER";
    }

    private String generateEmail(String realName) {
        String pinyin = convertToPinyin(realName);
        if (pinyin == null || pinyin.isEmpty()) {
            pinyin = "teacher";
        }
        return pinyin.toLowerCase() + "@swjtu.edu.cn";
    }

    private String generatePhone() {
        Random random = new Random();
        return "138" + String.format("%08d", random.nextInt(100000000));
    }

    public TeacherTaskStatisticsVO getTeacherTaskStatistics(Long teacherId) {
        TeacherTaskStatisticsVO statistics = new TeacherTaskStatisticsVO();

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Task::getTeacherId, teacherId);

        Long total = taskMapper.selectCount(wrapper);
        statistics.setTotalTasks(total);

        statistics.setPendingUploadTasks(taskMapper.selectCount(
                new LambdaQueryWrapper<Task>().eq(Task::getTeacherId, teacherId).eq(Task::getStatus, "PENDING_UPLOAD")));
        statistics.setSubmittedTasks(taskMapper.selectCount(
                new LambdaQueryWrapper<Task>().eq(Task::getTeacherId, teacherId).eq(Task::getStatus, "SUBMITTED")));
        Long reviewingCount = taskMapper.selectCount(
                new LambdaQueryWrapper<Task>()
                        .eq(Task::getTeacherId, teacherId)
                        .in(Task::getStatus, "SUBMITTED", "REVIEWING"));
        statistics.setReviewingTasks(reviewingCount);
        statistics.setApprovedTasks(taskMapper.selectCount(
                new LambdaQueryWrapper<Task>().eq(Task::getTeacherId, teacherId).eq(Task::getStatus, "APPROVED")));
        statistics.setNeedRevisionTasks(taskMapper.selectCount(
                new LambdaQueryWrapper<Task>().eq(Task::getTeacherId, teacherId).eq(Task::getStatus, "NEED_REVISION")));

        if (total > 0) {
            double rate = (statistics.getApprovedTasks().doubleValue() / total) * 100;
            statistics.setCompletionRate(Math.round(rate * 100.0) / 100.0);
        } else {
            statistics.setCompletionRate(0.0);
        }
        return statistics;
    }
}

