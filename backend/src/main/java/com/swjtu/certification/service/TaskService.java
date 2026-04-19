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
import com.swjtu.certification.mapper.ReviewRecordMapper;
import com.swjtu.certification.mapper.TaskMapper;
import com.swjtu.certification.mapper.TeacherMapper;
import com.swjtu.certification.mapper.UserMapper;
import com.swjtu.certification.mapper.FileMapper;
import com.swjtu.certification.util.TaskTeacherUtils;
import com.swjtu.certification.util.TaskItemUtils;
import com.swjtu.certification.util.TeacherNameUtils;
import com.swjtu.certification.vo.ReviewProjectVO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Random;

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
    private final ReviewRecordMapper reviewRecordMapper;
    private final NotificationService notificationService;
    private final ReviewWorkflowService reviewWorkflowService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final EmailService emailService;

    private LambdaQueryWrapper<Task> applyTeacherTaskFilter(LambdaQueryWrapper<Task> wrapper, Long teacherId) {
        return wrapper.and(w -> w.eq(Task::getTeacherId, teacherId)
                .or().eq(Task::getTeacherId2, teacherId)
                .or().eq(Task::getTeacherId3, teacherId)
                .or().eq(Task::getTeacherId4, teacherId));
    }

    private List<User> getTaskTeachers(Task task) {
        return TaskTeacherUtils.getTeacherIds(task).stream()
                .map(userMapper::selectById)
                .filter(user -> user != null)
                .collect(Collectors.toList());
    }

    private String joinTeacherNames(Task task) {
        List<User> teachers = getTaskTeachers(task);
        return teachers.isEmpty()
                ? "未分配教师"
                : teachers.stream().map(User::getRealName).filter(name -> name != null && !name.isEmpty()).collect(Collectors.joining(" "));
    }

    private String joinTeacherWorkIds(Task task) {
        return getTaskTeachers(task).stream()
                .map(User::getWorkId)
                .filter(workId -> workId != null && !workId.isEmpty())
                .collect(Collectors.joining(" "));
    }

    private User getOrCreateTeacherUserEntity(String teacherInput) {
        if (teacherInput == null || teacherInput.trim().isEmpty()) {
            return null;
        }
        String normalizedInput = teacherInput.trim();

        LambdaQueryWrapper<User> wrapperByWorkId = new LambdaQueryWrapper<>();
        wrapperByWorkId.eq(User::getWorkId, normalizedInput);
        User user = userMapper.selectOne(wrapperByWorkId);
        if (user != null && "TEACHER".equals(user.getRole())) {
            return user;
        }

        LambdaQueryWrapper<User> wrapperByName = new LambdaQueryWrapper<>();
        wrapperByName.eq(User::getRealName, normalizedInput)
                .eq(User::getRole, "TEACHER");
        user = userMapper.selectOne(wrapperByName);
        if (user != null) {
            return user;
        }

        String workId = normalizedInput.matches("\\d+") ? normalizedInput : generateWorkId(normalizedInput);
        LambdaQueryWrapper<User> workIdWrapper = new LambdaQueryWrapper<>();
        workIdWrapper.eq(User::getWorkId, workId);
        if (userMapper.selectCount(workIdWrapper) > 0) {
            workId = workId + System.currentTimeMillis();
        }

        String username = "teacher_" + workId;
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, username);
        if (userMapper.selectCount(usernameWrapper) > 0) {
            username = username + "_" + System.currentTimeMillis();
        }

        User newTeacher = new User();
        newTeacher.setUsername(username);
        newTeacher.setWorkId(workId);
        newTeacher.setRealName(normalizedInput);
        newTeacher.setEmail(generateEmail(normalizedInput));
        newTeacher.setPhone(generatePhone());
        newTeacher.setPassword(passwordEncoder.encode("123456"));
        newTeacher.setRole("TEACHER");
        newTeacher.setStatus(1);
        newTeacher.setCreateTime(LocalDateTime.now());
        newTeacher.setUpdateTime(LocalDateTime.now());
        userMapper.insert(newTeacher);
        return newTeacher;
    }

    private List<User> resolveTeacherUsersByRawNames(String rawTeacherNames) {
        List<User> teachers = TeacherNameUtils.splitTeacherNames(rawTeacherNames).stream()
                .map(this::getOrCreateTeacherUserEntity)
                .filter(user -> user != null)
                .collect(Collectors.toList());
        return teachers.size() > 4 ? teachers.subList(0, 4) : teachers;
    }

    private List<User> resolveTeacherUsersForTeacherRecord(Teacher teacherEntity, Long preferredTeacherId) {
        List<User> teachers = resolveTeacherUsersByRawNames(teacherEntity != null ? teacherEntity.getTeacherName() : null);
        if (preferredTeacherId != null) {
            User preferredTeacher = userMapper.selectById(preferredTeacherId);
            if (preferredTeacher != null) {
                teachers.removeIf(user -> user.getId().equals(preferredTeacherId));
                teachers.add(0, preferredTeacher);
            }
        }
        return teachers.size() > 4 ? teachers.subList(0, 4) : teachers;
    }

    private boolean matchesTeacherRecord(Task task, Teacher teacherEntity) {
        if (task == null || teacherEntity == null) {
            return false;
        }
        List<String> teacherNames = TeacherNameUtils.splitTeacherNames(teacherEntity.getTeacherName());
        if (teacherNames.isEmpty()) {
            return false;
        }
        Set<String> taskTeacherNames = getTaskTeachers(task).stream()
                .map(User::getRealName)
                .filter(name -> name != null && !name.isEmpty())
                .collect(Collectors.toSet());
        return teacherNames.stream().anyMatch(taskTeacherNames::contains);
    }


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
        for (BatchTaskDTO.TaskItem item : batchTaskDTO.getTasks()) {
            Teacher teacherEntity = teacherMapper.selectById(item.getCourseId());
            if (teacherEntity == null) {
                throw new RuntimeException("课程信息不存在，ID: " + item.getCourseId());
            }

            List<User> taskTeachers = resolveTeacherUsersForTeacherRecord(teacherEntity, item.getTeacherId());
            if (taskTeachers.isEmpty() && item.getTeacherId() != null) {
                User teacher = userMapper.selectById(item.getTeacherId());
                if (teacher != null) {
                    taskTeachers.add(teacher);
                }
            }
            if (taskTeachers.isEmpty()) {
                throw new RuntimeException("教师不存在，课程ID: " + item.getCourseId());
            }

            if (item.getAssessorId() != null) {
                User assessor = userMapper.selectById(item.getAssessorId());
                if (assessor == null) {
                    throw new RuntimeException("审核员不存在，ID: " + item.getAssessorId());
                }
                if (taskTeachers.stream().anyMatch(teacher -> teacher.getId().equals(item.getAssessorId()))) {
                    throw new RuntimeException("审核员不能是教师本人");
                }
            }

            LambdaQueryWrapper<Task> activeTaskWrapper = new LambdaQueryWrapper<>();
            activeTaskWrapper.eq(Task::getCourseId, item.getCourseId())
                    .in(Task::getStatus, "PENDING_UPLOAD", "SUBMITTED", "REVIEWING", "NEED_REVISION");
            List<Task> existingTasks = taskMapper.selectList(activeTaskWrapper);
            Set<Long> newTeacherIds = taskTeachers.stream().map(User::getId).collect(Collectors.toSet());
            boolean hasConflict = existingTasks.stream().anyMatch(existingTask ->
                    TaskTeacherUtils.getTeacherIds(existingTask).stream().anyMatch(newTeacherIds::contains));
            if (hasConflict) {
                throw new RuntimeException("该课程已有相同教师的未完成任务，课程ID: " + item.getCourseId());
            }

            Task task = new Task();
            task.setCourseId(item.getCourseId());
            TaskTeacherUtils.assignTeacherIds(task, taskTeachers.stream().map(User::getId).collect(Collectors.toList()));
            task.setAssessorId(item.getAssessorId());
            task.setDeadline(item.getDeadline());
            task.setStatus("PENDING_UPLOAD");
            task.setCreateTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.insert(task);

            String teacherNotificationTitle = "新任务通知";
            String teacherNotificationContent = String.format(
                    "您收到了一个新的备案任务。课程：%s，教学班：%s，截止日期：%s。请及时完成材料上传。",
                    teacherEntity.getCourseName(),
                    teacherEntity.getTeachingClass(),
                    item.getDeadline().toString()
            );
            for (User teacher : taskTeachers) {
                notificationService.createNotification(
                        teacher.getId(),
                        task.getId(),
                        "NEW_TASK",
                        teacherNotificationTitle,
                        teacherNotificationContent
                );
            }

            if (item.getAssessorId() != null) {
                String assessorNotificationTitle = "新审核任务通知";
                String assessorNotificationContent = String.format(
                        "您收到了一个新的审核任务。课程：%s，教学班：%s，教师：%s，截止日期：%s。请及时进行审核。",
                        teacherEntity.getCourseName(),
                        teacherEntity.getTeachingClass(),
                        joinTeacherNames(task),
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

        vo.setStatusDesc(getTaskStatusDesc(task.getStatus()));
        vo.setAvailableReviewProjects(reviewWorkflowService.getAvailableReviewProjects(task));

        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null && !matchesTeacherRecord(task, teacherEntity)) {
                teacherEntity = null;
            }
        }

        if (teacherEntity != null) {
            vo.setCourseName(teacherEntity.getCourseName());
            vo.setCourseCode(teacherEntity.getCourseCode());
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
                vo.setCourseCode("");
                vo.setTeachingClass("未开课");
                vo.setGrade(course.getGrade());
                vo.setSemester(course.getSemester());
            } else {
                vo.setCourseName("未知课程");
                vo.setCourseCode("");
                vo.setTeachingClass("");
            }
        }

        if (!getTaskTeachers(task).isEmpty()) {
            vo.setTeacherWorkId(joinTeacherWorkIds(task));
            vo.setTeacherName(joinTeacherNames(task));
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
        applyTeacherTaskFilter(wrapper, teacherId);

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
        if (!TaskTeacherUtils.containsTeacher(task, teacherId)) {
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
        if (!TaskTeacherUtils.containsTeacher(task, teacherId)) {
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

        List<File> uploadedFiles = fileMapper.selectList(fileWrapper);

        List<TaskItemUtils.ItemConfig> requiredItems = TaskItemUtils.parseRequiredItems(task.getMaterialRequirements());
        if (!requiredItems.isEmpty()) {
            Set<String> uploadedItemCodes = uploadedFiles.stream()
                    .map(file -> TaskItemUtils.resolveItemCodeFromPath(file.getFilePath()))
                    .filter(code -> code != null && !code.trim().isEmpty())
                    .collect(Collectors.toSet());

            List<String> missingItemNames = new ArrayList<>();
            for (TaskItemUtils.ItemConfig item : requiredItems) {
                if (!uploadedItemCodes.contains(item.getCode())) {
                    missingItemNames.add(item.getName());
                }
            }

            if (!missingItemNames.isEmpty()) {
                throw new RuntimeException("以下备案项目尚未上传文件：" + String.join("、", missingItemNames));
            }
        }

        // 检查状态
        boolean canResubmit = "PENDING_UPLOAD".equals(task.getStatus()) || reviewWorkflowService.hasRejectedProjects(taskId);
        if (!canResubmit) {
            throw new RuntimeException("当前任务状态不允许提交");
        }

        boolean isResubmission = !"PENDING_UPLOAD".equals(task.getStatus());

        if ("PENDING_UPLOAD".equals(task.getStatus())) {
            task.setStatus("SUBMITTED");
        } else {
            validateRejectedItemsResubmission(task, uploadedFiles);
            reviewWorkflowService.reopenRejectedProjects(taskId);
        }
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        reviewWorkflowService.refreshTaskStatus(taskId);

        // ========== 给审核员发送通知 ==========
        List<Long> assessorIds = reviewWorkflowService.getActiveAssessorIds(taskId);
        if (!assessorIds.isEmpty()) {
            String teacherNames = joinTeacherNames(task);
            Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
            String courseName = teacherEntity != null ? teacherEntity.getCourseName() : "未知课程";
            String teachingClass = teacherEntity != null ? teacherEntity.getTeachingClass() : "";

            for (Long assessorId : assessorIds) {
                User assessor = userMapper.selectById(assessorId);
                if (assessor != null) {
                    List<ReviewProjectVO> activeProjects = reviewWorkflowService.getAssessorActiveProjects(taskId, assessorId);
                    String projectText = activeProjects.isEmpty()
                            ? "备案材料"
                            : activeProjects.stream().map(ReviewProjectVO::getName).collect(Collectors.joining("、"));
                    String title = isResubmission ? "任务重新提交待审核通知" : "任务待审核通知";
                    String content = String.format("教师 %s 已%s课程 %s（教学班 %s）的备案材料。待审核项目：%s，请及时审核。",
                            teacherNames,
                            isResubmission ? "重新提交" : "提交",
                            courseName,
                            teachingClass,
                            projectText);
                    notificationService.createNotification(assessorId, taskId, "TASK_SUBMITTED", title, content);
                }

                if (assessor != null && assessor.getEmail() != null && !assessor.getEmail().isEmpty()) {
                    List<ReviewProjectVO> activeProjects = reviewWorkflowService.getAssessorActiveProjects(taskId, assessorId);
                    String projectText = activeProjects.isEmpty()
                            ? "备案材料"
                            : activeProjects.stream().map(ReviewProjectVO::getName).collect(Collectors.joining("、"));
                    String subject = isResubmission
                            ? "【专业认证备案】任务重新提交待审核 - " + courseName
                            : "【专业认证备案】任务待审核 - " + courseName;
                    String content = buildTaskSubmittedEmailContent(courseName, teachingClass, teacherNames, projectText, isResubmission);
                    emailService.sendHtmlMail(assessor.getEmail(), subject, content);
                }
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

        vo.setStatusDesc(getTaskStatusDesc(task.getStatus()));

        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null && !matchesTeacherRecord(task, teacherEntity)) {
                teacherEntity = null;
            }
        }

        if (teacherEntity != null) {
            vo.setCourseName(teacherEntity.getCourseName());
            vo.setCourseCode(teacherEntity.getCourseCode());
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
                vo.setCourseCode("");
                vo.setTeachingClass("未开课");
                vo.setGrade(course.getGrade());
                vo.setSemester(course.getSemester());
            } else {
                vo.setCourseName("未知课程");
                vo.setCourseCode("");
                vo.setTeachingClass("");
            }
        }

        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getTaskId, task.getId());
        fileWrapper.eq(File::getStatus, "UPLOADED");
        Long fileCount = fileMapper.selectCount(fileWrapper);
        vo.setFileCount(fileCount.intValue());

        List<ReviewProjectVO> reviewProjects = reviewWorkflowService.getTeacherProjects(task);
        vo.setIsExpired(LocalDateTime.now().isAfter(task.getDeadline()));
        vo.setReviewProjects(reviewProjects);
        vo.setRejectedReviewProjects(reviewProjects.stream()
                .filter(project -> "REJECTED".equals(project.getStatus()))
                .collect(Collectors.toList()));
        vo.setCanUpload(reviewProjects.stream().anyMatch(project -> Boolean.TRUE.equals(project.getEditable()))
                || (reviewProjects.isEmpty() && "PENDING_UPLOAD".equals(task.getStatus())));

        return vo;
    }

    /**
     * 获取审核员的任务列表
     */
    public List<AssessorTaskVO> getAssessorTasks(Long assessorId, String status, Long teacherId) {
        List<Long> taskIds = reviewWorkflowService.getAssessorActiveTaskIds(assessorId);
        if (taskIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Task::getId, taskIds);
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Task::getStatus, status);
        }
        if (teacherId != null) {
            applyTeacherTaskFilter(wrapper, teacherId);
        }
        wrapper.orderByDesc(Task::getCreateTime);

        List<Task> tasks = taskMapper.selectList(wrapper);
        return tasks.stream().map(task -> convertToAssessorTaskVO(task, assessorId)).collect(Collectors.toList());
    }

    /**
     * 获取审核员的任务详情
     */
    public AssessorTaskVO getAssessorTaskDetail(Long taskId, Long assessorId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (!reviewWorkflowService.canAssessorReviewTask(taskId, assessorId)) {
            throw new RuntimeException("无权查看该任务");
        }
        return convertToAssessorTaskVO(task, assessorId);
    }

    /**
     * 获取审核统计信息
     */
    public ReviewStatisticsVO getReviewStatistics(Long assessorId) {
        ReviewStatisticsVO statistics = new ReviewStatisticsVO();

        List<Long> taskIds = reviewWorkflowService.getAssessorTaskIds(assessorId);
        statistics.setTotalTasks((long) taskIds.size());
        if (taskIds.isEmpty()) {
            statistics.setPendingTasks(0L);
            statistics.setReviewingTasks(0L);
            statistics.setApprovedTasks(0L);
            statistics.setNeedRevisionTasks(0L);
            statistics.setProcessedTasks(0L);
            statistics.setApprovalRate(0.0);
            return statistics;
        }

        List<Task> tasks = taskMapper.selectBatchIds(taskIds);
        statistics.setPendingTasks(tasks.stream().filter(task -> "SUBMITTED".equals(task.getStatus())).count());
        statistics.setReviewingTasks(tasks.stream().filter(task -> "REVIEWING".equals(task.getStatus())).count());
        statistics.setApprovedTasks(tasks.stream().filter(task -> "APPROVED".equals(task.getStatus())).count());
        statistics.setNeedRevisionTasks(0L);

        Long processedTasks = statistics.getApprovedTasks() + statistics.getReviewingTasks();
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
    private AssessorTaskVO convertToAssessorTaskVO(Task task, Long assessorId) {
        AssessorTaskVO vo = new AssessorTaskVO();
        vo.setId(task.getId());
        vo.setCourseId(task.getCourseId());
        vo.setDeadline(task.getDeadline());
        vo.setStatus(task.getStatus());
        vo.setDescription(task.getDescription());
        vo.setMaterialRequirements(task.getMaterialRequirements());
        vo.setCreateTime(task.getCreateTime());

        vo.setStatusDesc(getTaskStatusDesc(task.getStatus()));

        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null && !matchesTeacherRecord(task, teacherEntity)) {
                teacherEntity = null;
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

        if (!getTaskTeachers(task).isEmpty()) {
            vo.setTeacherId(task.getTeacherId());
            vo.setTeacherWorkId(joinTeacherWorkIds(task));
            vo.setTeacherName(joinTeacherNames(task));
        } else {
            vo.setTeacherName("未分配教师");
        }

        LambdaQueryWrapper<File> fileWrapper = new LambdaQueryWrapper<>();
        fileWrapper.eq(File::getTaskId, task.getId());
        fileWrapper.eq(File::getStatus, "UPLOADED");
        Long fileCount = fileMapper.selectCount(fileWrapper);
        vo.setFileCount(fileCount.intValue());
        vo.setAssignedReviewProjects(reviewWorkflowService.getAssessorProjects(task.getId(), assessorId));

        if ("SUBMITTED".equals(task.getStatus()) || "REVIEWING".equals(task.getStatus()) ||
                "APPROVED".equals(task.getStatus())) {
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
        applyTeacherTaskFilter(wrapper, teacherId);

        Long total = taskMapper.selectCount(wrapper);
        statistics.setTotalTasks(total);

        LambdaQueryWrapper<Task> pendingWrapper = new LambdaQueryWrapper<>();
        applyTeacherTaskFilter(pendingWrapper, teacherId);
        pendingWrapper.eq(Task::getStatus, "PENDING_UPLOAD");
        statistics.setPendingUploadTasks(taskMapper.selectCount(pendingWrapper));

        LambdaQueryWrapper<Task> submittedWrapper = new LambdaQueryWrapper<>();
        applyTeacherTaskFilter(submittedWrapper, teacherId);
        submittedWrapper.eq(Task::getStatus, "SUBMITTED");
        statistics.setSubmittedTasks(taskMapper.selectCount(submittedWrapper));

        Long reviewingCount = taskMapper.selectCount(
                applyTeacherTaskFilter(new LambdaQueryWrapper<>(), teacherId)
                        .in(Task::getStatus, "SUBMITTED", "REVIEWING"));
        statistics.setReviewingTasks(reviewingCount);

        LambdaQueryWrapper<Task> approvedWrapper = new LambdaQueryWrapper<>();
        applyTeacherTaskFilter(approvedWrapper, teacherId);
        approvedWrapper.eq(Task::getStatus, "APPROVED");
        statistics.setApprovedTasks(taskMapper.selectCount(approvedWrapper));
        statistics.setNeedRevisionTasks(0L);

        if (total > 0) {
            double rate = (statistics.getApprovedTasks().doubleValue() / total) * 100;
            statistics.setCompletionRate(Math.round(rate * 100.0) / 100.0);
        } else {
            statistics.setCompletionRate(0.0);
        }
        return statistics;
    }

    private String buildTaskSubmittedEmailContent(String courseName, String teachingClass, String teacherName, String projectText, boolean isResubmission) {
        return String.format(
                "<h3>📬 专业认证年度备案 - 任务待审核</h3>" +
                        "<p>教师 <strong>%s</strong> 已%s备案材料，请及时审核。</p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>课程名称：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>教学班：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>待审核项目：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://localhost:8080/assessor/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>进入系统审核</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                teacherName, isResubmission ? "重新提交了" : "提交了", courseName, teachingClass, projectText
        );
    }

    private String getTaskStatusDesc(String status) {
        switch (status) {
            case "PENDING_UPLOAD":
                return "待上传";
            case "SUBMITTED":
                return "待审核";
            case "PENDING_REVIEW":
            case "REVIEWING":
                return "审核中";
            case "APPROVED":
                return "审核通过";
            case "NEED_REVISION":
                return "需修改";
            default:
                return "未知";
        }
    }

    private void validateRejectedItemsResubmission(Task task, List<File> uploadedFiles) {
        List<ReviewProjectVO> rejectedProjects = reviewWorkflowService.getRejectedProjects(task);
        if (rejectedProjects.isEmpty()) {
            throw new RuntimeException("当前没有需要重新提交的备案目录");
        }

        Map<String, LocalDateTime> latestRejectedTimeByItem = reviewRecordMapper.selectList(
                new LambdaQueryWrapper<com.swjtu.certification.entity.ReviewRecord>()
                        .eq(com.swjtu.certification.entity.ReviewRecord::getTaskId, task.getId())
                        .eq(com.swjtu.certification.entity.ReviewRecord::getReviewStatus, "REJECTED")
                        .orderByDesc(com.swjtu.certification.entity.ReviewRecord::getReviewTime)
        ).stream().collect(Collectors.toMap(
                com.swjtu.certification.entity.ReviewRecord::getItemCode,
                com.swjtu.certification.entity.ReviewRecord::getReviewTime,
                (first, second) -> first
        ));

        List<String> notUpdatedProjects = new ArrayList<>();
        for (ReviewProjectVO project : rejectedProjects) {
            LocalDateTime latestRejectedTime = latestRejectedTimeByItem.get(project.getCode());
            boolean hasNewUpload = uploadedFiles.stream().anyMatch(file -> {
                String fileItemCode = TaskItemUtils.resolveItemCodeFromPath(file.getFilePath());
                return project.getCode().equals(fileItemCode)
                        && (latestRejectedTime == null || file.getUploadTime().isAfter(latestRejectedTime));
            });
            if (!hasNewUpload) {
                notUpdatedProjects.add(project.getName());
            }
        }

        if (!notUpdatedProjects.isEmpty()) {
            throw new RuntimeException("以下未通过的备案目录需重新上传文件后才能再次提交：" + String.join("、", notUpdatedProjects));
        }
    }
}

