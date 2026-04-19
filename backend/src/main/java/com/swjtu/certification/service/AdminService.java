package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.config.MajorMappingConfig;
import com.swjtu.certification.dto.AddMemberDTO;
import com.swjtu.certification.dto.BatchTaskDTO;
import com.swjtu.certification.dto.GenerateTaskDTO;
import com.swjtu.certification.entity.Course;
import com.swjtu.certification.entity.Notification;
import com.swjtu.certification.entity.Task;
import com.swjtu.certification.entity.Teacher;
import com.swjtu.certification.entity.User;
import com.swjtu.certification.entity.ReviewRecord;
import com.swjtu.certification.mapper.CourseMapper;
import com.swjtu.certification.mapper.NotificationMapper;
import com.swjtu.certification.mapper.TaskMapper;
import com.swjtu.certification.mapper.TeacherMapper;
import com.swjtu.certification.mapper.UserMapper;
import com.swjtu.certification.mapper.FileMapper;
import com.swjtu.certification.mapper.ReviewRecordMapper;
import com.swjtu.certification.util.CourseNameUtils;
import com.swjtu.certification.util.GetCourseList;
import com.swjtu.certification.util.GetTeacherList;
import com.swjtu.certification.util.ReadWordToCsvUtil;
import com.swjtu.certification.util.SemesterUtils;
import com.swjtu.certification.util.TaskTeacherUtils;
import com.swjtu.certification.util.TaskItemUtils;
import com.swjtu.certification.util.TeacherNameUtils;
import com.swjtu.certification.vo.CourseVO;
import com.swjtu.certification.vo.PageResult;
import com.swjtu.certification.vo.TaskVO;
import com.swjtu.certification.vo.UserVO;
import com.swjtu.certification.vo.CourseItemVO;
import com.swjtu.certification.vo.CourseTeachingVO;
import com.swjtu.certification.vo.ArchiveTaskVO;
import com.swjtu.certification.vo.ReviewProjectVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final TaskMapper taskMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final TeacherMapper teacherMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;
    private final FileMapper fileMapper;
    private final ReviewRecordMapper reviewRecordMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final GetCourseList getCourseList;
    private final GetTeacherList getTeacherList;
    private final MajorMappingConfig majorMappingConfig;
    private final ReviewWorkflowService reviewWorkflowService;
    private static final Map<String, LocalDateTime> TEACHER_FETCH_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_DAYS = 30;
    private final EmailService emailService;

    @Value("${file.download.path:./downloads}")
    private String downloadPath;

    private static final Map<String, String> ITEM_DESCRIPTION_MAP = new HashMap<>();
    static {
        ITEM_DESCRIPTION_MAP.put("1", "教材封面及目录");
        ITEM_DESCRIPTION_MAP.put("2", "课程大纲");
        ITEM_DESCRIPTION_MAP.put("3", "电子教案");
        ITEM_DESCRIPTION_MAP.put("4", "课程评分标准");
        ITEM_DESCRIPTION_MAP.put("5", "课程目标达成度评价表");
        ITEM_DESCRIPTION_MAP.put("6", "空白试卷");
        ITEM_DESCRIPTION_MAP.put("7", "试卷参考答案及评分标准");
        ITEM_DESCRIPTION_MAP.put("8", "15份学生试卷");
        ITEM_DESCRIPTION_MAP.put("9", "成绩单(平时成绩、总评成绩)");
        ITEM_DESCRIPTION_MAP.put("10", "成绩分析表");
        ITEM_DESCRIPTION_MAP.put("11", "课程设计报告");
        ITEM_DESCRIPTION_MAP.put("12", "作业");
    }

    public Map<String, Object> getDashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        
        dashboard.put("totalTasks", taskMapper.selectCount(null));
        dashboard.put("totalUsers", userMapper.selectCount(null));
        
        LambdaQueryWrapper<Task> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(Task::getStatus, "PENDING_UPLOAD");
        dashboard.put("pendingTasks", taskMapper.selectCount(pendingWrapper));
        
        LambdaQueryWrapper<Task> submittedWrapper = new LambdaQueryWrapper<>();
        submittedWrapper.eq(Task::getStatus, "SUBMITTED");
        dashboard.put("submittedTasks", taskMapper.selectCount(submittedWrapper));
        
        LambdaQueryWrapper<Task> reviewingWrapper = new LambdaQueryWrapper<>();
        reviewingWrapper.eq(Task::getStatus, "REVIEWING");
        dashboard.put("reviewingTasks", taskMapper.selectCount(reviewingWrapper));
        
        LambdaQueryWrapper<Task> needRevisionWrapper = new LambdaQueryWrapper<>();
        needRevisionWrapper.eq(Task::getStatus, "NEED_REVISION");
        dashboard.put("needRevisionTasks", taskMapper.selectCount(needRevisionWrapper));
        
        LambdaQueryWrapper<Task> approvedWrapper = new LambdaQueryWrapper<>();
        approvedWrapper.eq(Task::getStatus, "APPROVED");
        dashboard.put("approvedTasks", taskMapper.selectCount(approvedWrapper));
        
        return dashboard;
    }

    public List<TaskVO> getAllTasks(String status, Integer page, Integer pageSize) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Task::getStatus, status);
        }
        
        wrapper.orderByDesc(Task::getCreateTime);
        
        if (page != null && pageSize != null) {
            wrapper.last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize));
        }
        
        List<Task> tasks = taskMapper.selectList(wrapper);
        return tasks.stream().map(this::convertToTaskVO).collect(Collectors.toList());
    }

    public PageResult<CourseVO> getAllCourses(String grade, String major, String semester, String status, String search, Integer page, Integer pageSize) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        
        if (grade != null && !grade.trim().isEmpty()) {
            wrapper.eq(Course::getGrade, grade);
        }
        
        if (major != null && !major.trim().isEmpty()) {
            wrapper.eq(Course::getMajor, major);
        }
        
        if (semester != null && !semester.trim().isEmpty()) {
            wrapper.like(Course::getSemester, semester);
        }
        
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq(Course::getStatus, Integer.parseInt(status));
        }
        
        if (search != null && !search.trim().isEmpty()) {
            wrapper.and(w -> w.like(Course::getCourseName, search)
                    .or()
                    .like(Course::getGrade, search)
                    .or()
                    .like(Course::getSemester, search)
                    .or()
                    .like(Course::getMajor, search));
        }
        
        wrapper.orderByDesc(Course::getCreateTime);
        
        Long total = courseMapper.selectCount(wrapper);
        
        if (page != null && pageSize != null) {
            wrapper.last("LIMIT " + pageSize + " OFFSET " + ((page - 1) * pageSize));
        }
        
        List<Course> courses = courseMapper.selectList(wrapper);
        List<CourseVO> courseVOs = courses.stream().map(this::convertToCourseVO).collect(Collectors.toList());
        return PageResult.of(courseVOs, page, pageSize, total);
    }

    public List<String> getAllGrades() {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Course::getGrade);
        wrapper.isNotNull(Course::getGrade);
        wrapper.groupBy(Course::getGrade);
        wrapper.orderByDesc(Course::getGrade);
        
        List<Course> courses = courseMapper.selectList(wrapper);
        return courses.stream()
                .map(Course::getGrade)
                .filter(grade -> grade != null && !grade.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getAllSemesters(String grade) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Teacher::getSemester);
        wrapper.isNotNull(Teacher::getSemester);
        wrapper.groupBy(Teacher::getSemester);
        wrapper.orderByDesc(Teacher::getSemester);

        List<Teacher> teachers = teacherMapper.selectList(wrapper);
        return teachers.stream()
                .map(Teacher::getSemester)
                .filter(semester -> semester != null && !semester.trim().isEmpty())
                .filter(semester -> {
                    // 只保留标准格式的学期（如 2025-2026-2），过滤异常数据（如 "1"）
                    return semester.matches("\\d{4}-\\d{4}-[12]");
                })
                .map(semester -> {
                    // 将标准格式转换为导出格式（如 2025-2026学年第二学期）
                    return SemesterUtils.toExportFormat(semester);
                })
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getAllMajors() {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Course::getMajor);
        wrapper.isNotNull(Course::getMajor);
        wrapper.groupBy(Course::getMajor);
        wrapper.orderByAsc(Course::getMajor);
        
        List<Course> courses = courseMapper.selectList(wrapper);
        return courses.stream()
                .map(Course::getMajor)
                .filter(major -> major != null && !major.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<UserVO> getAllTeachers(String code, String name) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "TEACHER");
        
        if (code != null && !code.trim().isEmpty()) {
            wrapper.like(User::getWorkId, code);
        }
        
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(User::getRealName, name);
        }
        
        wrapper.orderByAsc(User::getRealName);
        List<User> users = userMapper.selectList(wrapper);
        return users.stream().map(this::convertToUserVO).collect(Collectors.toList());
    }

    public List<UserVO> getAllAssessors(Long excludeTeacherId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "ASSESSOR");
        
        if (excludeTeacherId != null) {
            wrapper.ne(User::getId, excludeTeacherId);
        }
        
        wrapper.orderByAsc(User::getRealName);
        List<User> users = userMapper.selectList(wrapper);
        return users.stream().map(this::convertToUserVO).collect(Collectors.toList());
    }

    public List<String> getAllDepartments() {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Teacher::getDepartment);
        wrapper.isNotNull(Teacher::getDepartment);
        wrapper.groupBy(Teacher::getDepartment);
        wrapper.orderByAsc(Teacher::getDepartment);
        List<Teacher> teachers = teacherMapper.selectList(wrapper);
        return teachers.stream()
                .map(Teacher::getDepartment)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public void batchCreateTasks(BatchTaskDTO batchTaskDTO) {
        for (BatchTaskDTO.TaskItem item : batchTaskDTO.getTasks()) {
            Teacher teacherEntity = null;
            User teacher = null;
            List<User> taskTeachers = new ArrayList<>();

            if (item.getIsManual() != null && item.getIsManual()) {
                System.out.println("处理手动添加的任务");
                System.out.println("课程名称: " + item.getCourseName());
                System.out.println("教学班号: " + item.getTeachingClass());

                if (item.getTeacherId() != null) {
                    teacher = userMapper.selectById(item.getTeacherId());
                    if (teacher != null) {
                        taskTeachers.add(teacher);
                    }
                }
            } else {
                System.out.println("处理从课程列表选择的任务");

                teacherEntity = teacherMapper.selectById(item.getCourseId());
                if (teacherEntity == null) {
                    throw new RuntimeException("课程信息不存在，ID: " + item.getCourseId());
                }

                teacher = userMapper.selectById(item.getTeacherId());
                if (teacher == null) {
                    throw new RuntimeException("教师不存在，ID: " + item.getTeacherId());
                }
                taskTeachers = resolveTeacherUsersForTeacherRecord(teacherEntity, item.getTeacherId());
            }

            if (taskTeachers.isEmpty() && teacher != null) {
                taskTeachers.add(teacher);
            }

            // 审核员可空，仅当存在时进行校验
            if (item.getAssessorId() != null) {
                User assessor = userMapper.selectById(item.getAssessorId());
                if (assessor == null) {
                    throw new RuntimeException("审核员不存在，ID: " + item.getAssessorId());
                }
                if (taskTeachers.stream().anyMatch(user -> user.getId().equals(item.getAssessorId()))) {
                    throw new RuntimeException("审核员不能是教师本人");
                }
            }

            // 检查重复任务（仅对非手动添加的任务）
            if (item.getIsManual() == null || !item.getIsManual()) {
                LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
                taskWrapper.eq(Task::getCourseId, item.getCourseId());
                taskWrapper.and(w -> w.eq(Task::getStatus, "PENDING_UPLOAD")
                        .or().eq(Task::getStatus, "SUBMITTED")
                        .or().eq(Task::getStatus, "REVIEWING")
                        .or().eq(Task::getStatus, "NEED_REVISION"));
                Long existingTaskCount = taskMapper.selectCount(taskWrapper);
                if (existingTaskCount > 0) {
                    throw new RuntimeException("课程已被指派，课程ID: " + item.getCourseId());
                }
            }

            // 创建任务
            Task task = new Task();
            task.setCourseId(item.getCourseId());
            TaskTeacherUtils.assignTeacherIds(task, taskTeachers.stream().map(User::getId).collect(Collectors.toList()));
            task.setAssessorId(item.getAssessorId());   // 允许 null
            task.setDeadline(item.getDeadline());
            task.setDescription(item.getDescription());
            task.setMaterialRequirements(item.getMaterialRequirements());
            task.setStatus("PENDING_UPLOAD");
            task.setCreateTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.insert(task);

            String courseName = teacherEntity != null ? teacherEntity.getCourseName() : item.getCourseName();
            String teachingClass = teacherEntity != null ? teacherEntity.getTeachingClass() : item.getTeachingClass();

            // 给教师发送通知
            String teacherNotificationTitle = "新任务通知";
            String teacherNotificationContent = String.format(
                    "您收到了一个新的备案任务。课程：%s，教学班：%s，截止日期：%s。请及时完成材料上传。",
                    courseName,
                    teachingClass,
                    item.getDeadline().toString()
            );
            for (User taskTeacher : taskTeachers) {
                notificationService.createNotification(
                        taskTeacher.getId(),
                        task.getId(),
                        "NEW_TASK",
                        teacherNotificationTitle,
                        teacherNotificationContent
                );
            }

            // 给审核员发送通知（仅当存在审核员时）
            if (item.getAssessorId() != null) {
                String assessorNotificationTitle = "新审核任务通知";
                String assessorNotificationContent = String.format(
                        "您收到了一个新的审核任务。课程：%s，教学班：%s，教师：%s，截止日期：%s。请及时进行审核。",
                        courseName,
                        teachingClass,
                        joinTeacherNames(taskTeachers),
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

            // 发送邮件给教师
            for (User taskTeacher : taskTeachers) {
                if (taskTeacher.getEmail() != null && !taskTeacher.getEmail().isEmpty()) {
                    String subject = "【专业认证备案】新任务通知 - " + courseName;
                    String emailContent = buildTeacherEmailContent(
                            courseName,
                            teachingClass,
                            item.getDeadline().toString()
                    );
                    emailService.sendHtmlMail(taskTeacher.getEmail(), subject, emailContent);
                }
            }

            // 发送邮件给审核员（如果有）
            if (item.getAssessorId() != null) {
                User assessor = userMapper.selectById(item.getAssessorId());
                if (assessor != null && assessor.getEmail() != null && !assessor.getEmail().isEmpty()) {
                    String subject = "【专业认证备案】新审核任务通知 - " + courseName;
                    String emailContent = buildAssessorEmailContent(
                            courseName,
                            teachingClass,
                            joinTeacherNames(taskTeachers),
                            item.getDeadline().toString()
                    );
                    emailService.sendHtmlMail(assessor.getEmail(), subject, emailContent);
                }
            }
        }
    }

    @Transactional
    public void updateTaskDeadline(Long id, String deadline) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        task.setDeadline(LocalDateTime.parse(deadline));
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public void updateTaskDescription(Long id, String description) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (description != null && description.trim().isEmpty()) {
            description = null;
        }
        task.setDescription(description);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    public void updateTaskMaterialRequirements(Long id, String materialRequirements) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }
        if (materialRequirements != null && materialRequirements.trim().isEmpty()) {
            materialRequirements = null;
        }
        task.setMaterialRequirements(materialRequirements);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    @Transactional
    public void remindTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        TaskVO taskVO = convertToTaskVO(task);

        List<User> taskTeachers = getTaskTeachers(task);
        if (!taskTeachers.isEmpty()) {
            String teacherNotificationTitle = "任务催办提醒";
            String teacherNotificationContent = String.format(
                    "请尽快处理备案任务：课程《%s》%s，当前状态：%s，截止日期：%s。",
                    taskVO.getCourseName() == null ? "-" : taskVO.getCourseName(),
                    taskVO.getTeachingClass() == null || taskVO.getTeachingClass().trim().isEmpty() ? "" : "（教学班：" + taskVO.getTeachingClass() + "）",
                    taskVO.getStatusDesc() == null ? task.getStatus() : taskVO.getStatusDesc(),
                    task.getDeadline() == null ? "-" : task.getDeadline().toString()
            );
            for (User taskTeacher : taskTeachers) {
                notificationService.createNotification(
                        taskTeacher.getId(),
                        task.getId(),
                        "TASK_REMINDER",
                        teacherNotificationTitle,
                        teacherNotificationContent
                );
            }

            String courseName = taskVO.getCourseName();
            String teachingClass = taskVO.getTeachingClass();

// 给教师发邮件
            for (User teacher : taskTeachers) {
                if (teacher.getEmail() != null && !teacher.getEmail().isEmpty()) {
                    String subject = "【专业认证备案】催办提醒 - " + courseName;
                    String content = buildRemindEmailContent(courseName, teachingClass, taskVO.getStatusDesc(), task.getDeadline().toString());
                    emailService.sendHtmlMail(teacher.getEmail(), subject, content);
                }
            }

            // 给审核员发邮件
            if (task.getAssessorId() != null) {
                User assessor = userMapper.selectById(task.getAssessorId());
                if (assessor != null && assessor.getEmail() != null && !assessor.getEmail().isEmpty()) {
                    String subject = "【专业认证备案】催办提醒 - " + courseName;
                    String content = buildRemindEmailContent(courseName, teachingClass, taskVO.getStatusDesc(), task.getDeadline().toString());
                    emailService.sendHtmlMail(assessor.getEmail(), subject, content);
                }
            }
        }

        if (task.getAssessorId() != null) {
            String assessorNotificationTitle = "任务催办提醒";
            String assessorNotificationContent = String.format(
                    "请尽快处理审核任务：课程《%s》%s，当前状态：%s，截止日期：%s。",
                    taskVO.getCourseName() == null ? "-" : taskVO.getCourseName(),
                    taskVO.getTeachingClass() == null || taskVO.getTeachingClass().trim().isEmpty() ? "" : "（教学班：" + taskVO.getTeachingClass() + "）",
                    taskVO.getStatusDesc() == null ? task.getStatus() : taskVO.getStatusDesc(),
                    task.getDeadline() == null ? "-" : task.getDeadline().toString()
            );
            notificationService.createNotification(
                    task.getAssessorId(),
                    task.getId(),
                    "TASK_REMINDER",
                    assessorNotificationTitle,
                    assessorNotificationContent
            );
        }
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        
        statistics.put("totalTasks", taskMapper.selectCount(null));
        statistics.put("totalCourses", courseMapper.selectCount(null));
        statistics.put("totalUsers", userMapper.selectCount(null));
        
        LambdaQueryWrapper<Task> pendingWrapper = new LambdaQueryWrapper<>();
        pendingWrapper.eq(Task::getStatus, "PENDING_UPLOAD");
        statistics.put("pendingTasks", taskMapper.selectCount(pendingWrapper));
        
        LambdaQueryWrapper<Task> submittedWrapper = new LambdaQueryWrapper<>();
        submittedWrapper.eq(Task::getStatus, "SUBMITTED");
        statistics.put("submittedTasks", taskMapper.selectCount(submittedWrapper));
        
        LambdaQueryWrapper<Task> reviewingWrapper = new LambdaQueryWrapper<>();
        reviewingWrapper.eq(Task::getStatus, "REVIEWING");
        statistics.put("reviewingTasks", taskMapper.selectCount(reviewingWrapper));
        
        LambdaQueryWrapper<Task> approvedWrapper = new LambdaQueryWrapper<>();
        approvedWrapper.eq(Task::getStatus, "APPROVED");
        statistics.put("approvedTasks", taskMapper.selectCount(approvedWrapper));
        
        LambdaQueryWrapper<Task> needRevisionWrapper = new LambdaQueryWrapper<>();
        needRevisionWrapper.eq(Task::getStatus, "NEED_REVISION");
        statistics.put("needRevisionTasks", taskMapper.selectCount(needRevisionWrapper));
        
        LambdaQueryWrapper<Course> unassignedWrapper = new LambdaQueryWrapper<>();
        unassignedWrapper.eq(Course::getStatus, 0);
        statistics.put("unassignedCourses", courseMapper.selectCount(unassignedWrapper));
        
        LambdaQueryWrapper<Course> assignedWrapper = new LambdaQueryWrapper<>();
        assignedWrapper.eq(Course::getStatus, 1);
        statistics.put("assignedCourses", courseMapper.selectCount(assignedWrapper));
        
        int uploadedCourseFiles = countUploadedCourseFiles();
        statistics.put("uploadedCourseFiles", uploadedCourseFiles);
        
        return statistics;
    }
    
    private int countUploadedCourseFiles() {
        try {
            String userDir = System.getProperty("user.dir");
            String csvDir = userDir + File.separator + "uploads" + File.separator + "csv";
            Path csvPath = Paths.get(csvDir);
            
            if (!Files.exists(csvPath) || !Files.isDirectory(csvPath)) {
                return 0;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(csvPath, "*.csv")) {
                int count = 0;
                for (Path file : stream) {
                    count++;
                }
                return count;
            }
        } catch (IOException e) {
            System.err.println("统计上传文档数量失败: " + e.getMessage());
            return 0;
        }
    }
    
    public List<String> getUploadedCourseFiles() {
        try {
            String userDir = System.getProperty("user.dir");
            String csvDir = userDir + File.separator + "uploads" + File.separator + "csv";
            Path csvPath = Paths.get(csvDir);
            
            if (!Files.exists(csvPath) || !Files.isDirectory(csvPath)) {
                return new ArrayList<>();
            }
            
            List<String> fileNames = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(csvPath, "*.csv")) {
                for (Path file : stream) {
                    fileNames.add(file.getFileName().toString());
                }
            }
            return fileNames;
        } catch (IOException e) {
            System.err.println("获取上传文档列表失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Transactional
    public void deleteTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("任务不存在");
        }

        taskMapper.deleteById(id);

        Course course = courseMapper.selectById(task.getCourseId());
        if (course != null && course.getStatus() == 1) {
            LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
            taskWrapper.eq(Task::getCourseId, course.getId());
            Long remainingTaskCount = taskMapper.selectCount(taskWrapper);
            if (remainingTaskCount == 0) {
                course.setStatus(0);
                courseMapper.updateById(course);
            }
        }
    }

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

        // 设置状态描述
        vo.setStatusDesc(getTaskStatusDescription(task.getStatus()));
        vo.setAvailableReviewProjects(reviewWorkflowService.getAvailableReviewProjects(task));

        // 获取课程信息：优先从 teacher 表查询（教学班），若不存在则从 course 表查询（未来课程）
        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity != null && !matchesTeacherRecord(task, teacherEntity)) {
                teacherEntity = null;
            }
        }
        if (teacherEntity != null) {
            // 教学班信息
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
            // 未来课程（从 course 表获取）
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

        // 获取教师信息（支持多教师）
        List<User> taskTeachers = getTaskTeachers(task);
        if (!taskTeachers.isEmpty()) {
            vo.setTeacherWorkId(joinTeacherWorkIds(taskTeachers));
            vo.setTeacherName(joinTeacherNames(taskTeachers));
        } else {
            vo.setTeacherName("未分配教师");
        }

        // 获取审核员信息（如果有）
        if (task.getAssessorId() != null) {
            User assessor = userMapper.selectById(task.getAssessorId());
            if (assessor != null) {
                vo.setAssessorWorkId(assessor.getWorkId());
                vo.setAssessorName(assessor.getRealName());
            }
        }

        return vo;
    }

    private CourseVO convertToCourseVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setCourseName(course.getCourseName());
        vo.setGrade(course.getGrade());
        vo.setMajor(course.getMajor());
        vo.setSemester(course.getSemester());
        vo.setStatus(course.getStatus());
        vo.setStatusDesc(course.getStatus() == 0 ? "未指派" : "已指派");
        return vo;
    }

    private UserVO convertToUserVO(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setWorkId(user.getWorkId());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setRole(user.getRole());
        vo.setDepartment(user.getDepartment());
        vo.setTitle(user.getTitle());
        vo.setStatus(user.getStatus());
        return vo;
    }

    public List<UserVO> getAllMembers(String role, String code, String name) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        
        if (role != null && !role.trim().isEmpty()) {
            wrapper.eq(User::getRole, role);
        }
        
        if (code != null && !code.trim().isEmpty()) {
            wrapper.like(User::getWorkId, code);
        }
        
        if (name != null && !name.trim().isEmpty()) {
            wrapper.like(User::getRealName, name);
        }
        
        wrapper.orderByAsc(User::getRealName);
        List<User> users = userMapper.selectList(wrapper);
        return users.stream().map(this::convertToUserVO).collect(Collectors.toList());
    }

    @Transactional
    public void addMember(AddMemberDTO addMemberDTO) {
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, addMemberDTO.getUsername());
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        LambdaQueryWrapper<User> workIdWrapper = new LambdaQueryWrapper<>();
        workIdWrapper.eq(User::getWorkId, addMemberDTO.getWorkId());
        if (userMapper.selectCount(workIdWrapper) > 0) {
            throw new RuntimeException("工号已存在");
        }

        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, addMemberDTO.getEmail());
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new RuntimeException("邮箱已存在");
        }

        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(User::getPhone, addMemberDTO.getPhone());
        if (userMapper.selectCount(phoneWrapper) > 0) {
            throw new RuntimeException("手机号已存在");
        }

        if (!"ADMIN".equals(addMemberDTO.getRole()) && 
            !"TEACHER".equals(addMemberDTO.getRole()) && 
            !"ASSESSOR".equals(addMemberDTO.getRole())) {
            throw new RuntimeException("角色必须是ADMIN、TEACHER或ASSESSOR");
        }

        User user = new User();
        user.setUsername(addMemberDTO.getUsername());
        user.setWorkId(addMemberDTO.getWorkId());
        user.setRealName(addMemberDTO.getRealName());
        user.setEmail(addMemberDTO.getEmail());
        user.setPhone(addMemberDTO.getPhone());
        user.setPassword(passwordEncoder.encode(addMemberDTO.getPassword()));
        user.setRole(addMemberDTO.getRole());
        user.setDepartment(addMemberDTO.getDepartment());
        user.setTitle(addMemberDTO.getTitle());
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
    }

    @Transactional
    public void updateMemberStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new RuntimeException("不能禁用管理员");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void updateMemberRole(Long id, String role) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new RuntimeException("不能修改管理员的角色");
        }
        if (!"TEACHER".equals(role) && !"ASSESSOR".equals(role)) {
            throw new RuntimeException("角色必须是TEACHER或ASSESSOR");
        }
        user.setRole(role);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void deleteMember(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new RuntimeException("不能删除管理员");
        }

        LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.and(w -> w.eq(Task::getTeacherId, id)
                .or().eq(Task::getTeacherId2, id)
                .or().eq(Task::getTeacherId3, id)
                .or().eq(Task::getTeacherId4, id)
                .or().eq(Task::getAssessorId, id));
        if (taskMapper.selectCount(taskWrapper) > 0) {
            throw new RuntimeException("该用户有关联任务，无法删除");
        }

        userMapper.deleteById(id);
    }

    @Transactional
    public void uploadAndParseWordFile(MultipartFile file, String charset) throws Exception {
        String userDir = System.getProperty("user.dir");
        String uploadDir = userDir + File.separator + "uploads";
        Path uploadPath = Paths.get(uploadDir);
        
        String originalFilename = file.getOriginalFilename();
        String fileExtension = getFileExtension(originalFilename).toLowerCase();

        String subDir = "word";
        Path wordUploadDir = uploadPath.resolve(subDir);
        if (!Files.exists(wordUploadDir)) {
            Files.createDirectories(wordUploadDir);
        }

        Path wordFilePath = wordUploadDir.resolve(originalFilename);
        file.transferTo(wordFilePath.toFile());

        Path csvUploadDir = uploadPath.resolve("csv");
        if (!Files.exists(csvUploadDir)) {
            Files.createDirectories(csvUploadDir);
        }

        String csvFilename = originalFilename.substring(0, originalFilename.lastIndexOf(".")) + ".csv";
        Path csvPath = csvUploadDir.resolve(csvFilename);
        ReadWordToCsvUtil.readWord(wordFilePath.toString(), csvPath.toString());

        java.io.File csvFile = csvPath.toFile();
        if (!csvFile.exists()) {
            throw new RuntimeException("CSV文件生成失败");
        }

        List<com.swjtu.certification.entity.Course> courses = getCourseList.standardizeFile(csvPath, charset);

        for (com.swjtu.certification.entity.Course course : courses) {
            LambdaQueryWrapper<com.swjtu.certification.entity.Course> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(com.swjtu.certification.entity.Course::getCourseName, course.getCourseName());
            wrapper.eq(com.swjtu.certification.entity.Course::getGrade, course.getGrade());
            wrapper.eq(com.swjtu.certification.entity.Course::getSemester, course.getSemester());
            wrapper.eq(com.swjtu.certification.entity.Course::getMajor, course.getMajor());
            if (courseMapper.selectCount(wrapper) == 0) {
                courseMapper.insert(course);
            }
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf(".");
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    private String standardizeSemesterFormat(Course course) {
        String semester = course.getSemester();
        if (semester != null && !SemesterUtils.isValidStandardFormat(semester)) {
            if (course.getGrade() != null && course.getGrade().matches("\\d{4}")) {
                String semesterNumStr = semester.replaceAll("[^0-9]", "");
                if (!semesterNumStr.isEmpty()) {
                    try {
                        int semesterNum = Integer.parseInt(semesterNumStr);
                        int baseYear = Integer.parseInt(course.getGrade());
                        
                        int yearOffset = (semesterNum - 1) / 2;
                        int academicYear = baseYear + yearOffset;
                        String semesterFlag = (semesterNum % 2 == 1) ? "1" : "2";
                        
                        semester = academicYear + "-" + (academicYear + 1) + "-" + semesterFlag;
                    } catch (NumberFormatException e) {
                        semester = null;
                    }
                }
            }
        }
        return semester;
    }

    @Transactional
    public List<Teacher> fetchTeachersFromWeb(String courseName, String semester, String grade, String major) {
        System.out.println("=== 开始获取教师 ===");
        System.out.println("课程名: " + courseName + ", 学期: " + semester + ", 学年: " + grade + ", 专业: " + major);

        // 构建缓存键：专业_年级_学期_规整后的课程名
        String cacheKey = (major == null ? "" : major) + "_" +
                (grade == null ? "" : grade) + "_" +
                (semester == null ? "" : semester) + "_" +
                CourseNameUtils.buildNormalizedCacheKey(courseName);
        LocalDateTime lastFetchTime = TEACHER_FETCH_CACHE.get(cacheKey);
        boolean cacheValid = lastFetchTime != null &&
                lastFetchTime.isAfter(LocalDateTime.now().minusDays(CACHE_DURATION_DAYS));

        if (cacheValid) {
            // 缓存有效，直接从数据库读取
            List<Teacher> dbTeachers = findMatchingTeachersInDatabase(courseName, semester);
            if (dbTeachers != null && !dbTeachers.isEmpty()) {
                List<Teacher> filtered = filterTeachersByGradeAndMajor(dbTeachers, grade, major);
                System.out.println("使用缓存数据，返回教师数量: " + filtered.size());
                return filtered;
            }
        }

        // 缓存无效或数据库无数据，检查数据库是否已有该课程教师（可能是其他专业/年级爬取时存入的）
        List<Teacher> dbTeachers = findMatchingTeachersInDatabase(courseName, semester);

        if (dbTeachers != null && !dbTeachers.isEmpty()) {
            System.out.println("数据库中已存在该课程教师，数量: " + dbTeachers.size());
            List<Teacher> filteredTeachers = filterTeachersByGradeAndMajor(dbTeachers, grade, major);
            System.out.println("筛选后教师数量: " + filteredTeachers.size());
            // 更新缓存时间
            TEACHER_FETCH_CACHE.put(cacheKey, LocalDateTime.now());
            return filteredTeachers;
        }

        System.out.println("数据库中不存在该课程教师，开始从网页获取");

        List<Teacher> webTeachers = fetchTeachersFromWebByCourseNameRule(semester, courseName);
        System.out.println("从网页获取到教师数量: " + webTeachers.size());

        List<Teacher> result = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();

        for (Teacher webTeacher : webTeachers) {
            String uniqueKey = webTeacher.getTeacherName() + "_" +
                    webTeacher.getCourseName() + "_" +
                    webTeacher.getSemester() + "_" +
                    (webTeacher.getTeachingClass() != null ? webTeacher.getTeachingClass() : "");

            if (uniqueKeys.contains(uniqueKey)) {
                System.out.println("跳过重复教师: " + uniqueKey);
                continue;
            }
            uniqueKeys.add(uniqueKey);

            Teacher existing = findExistingTeacherRecord(webTeacher);

            if (existing == null) {
                webTeacher.setCreateTime(LocalDateTime.now());
                webTeacher.setUpdateTime(LocalDateTime.now());
                webTeacher.setStatus("PENDING");

                if (webTeacher.getPreferred() != null && webTeacher.getPreferred().length() > 255) {
                    webTeacher.setPreferred(webTeacher.getPreferred().substring(0, 255));
                }
                if (webTeacher.getTimeLocation() != null && webTeacher.getTimeLocation().length() > 255) {
                    webTeacher.setTimeLocation(webTeacher.getTimeLocation().substring(0, 255));
                }

                teacherMapper.insert(webTeacher);
                System.out.println("插入教师，ID: " + webTeacher.getId() + ", 姓名: " + webTeacher.getTeacherName());
                result.add(webTeacher);
            } else {
                System.out.println("教师已存在，ID: " + existing.getId() + ", 姓名: " + existing.getTeacherName());
                result.add(existing);
            }
        }

        // 更新缓存时间
        TEACHER_FETCH_CACHE.put(cacheKey, LocalDateTime.now());

        List<Teacher> filteredResult = filterTeachersByGradeAndMajor(result, grade, major);
        System.out.println("=== 获取教师完成，返回数量: " + filteredResult.size() + " ===");
        return filteredResult;
    }

    @Transactional
    public List<Teacher> fetchTeachersFromWebCurrent(String courseName) {
        System.out.println("=== 开始获取当前学期教师 ===");
        System.out.println("课程名: " + courseName);

        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        int flag = (month >= 2 && month <= 7) ? 2 : 1;
        int baseYear = (flag == 1 && month <= 12 && month >= 8) ? year : (flag == 2 ? year - 1 : year - 1);
        String currentSemester = baseYear + "-" + (baseYear + 1) + "-" + flag;

        // 缓存键：CURRENT_学期_规整后的课程名
        String cacheKey = "CURRENT_" + currentSemester + "_" + CourseNameUtils.buildNormalizedCacheKey(courseName);
        LocalDateTime lastFetchTime = TEACHER_FETCH_CACHE.get(cacheKey);
        boolean cacheValid = lastFetchTime != null &&
                lastFetchTime.isAfter(LocalDateTime.now().minusDays(CACHE_DURATION_DAYS));

        if (cacheValid) {
            List<Teacher> dbTeachers = findMatchingTeachersInDatabase(courseName, currentSemester);
            if (dbTeachers != null && !dbTeachers.isEmpty()) {
                System.out.println("使用缓存数据，返回教师数量: " + dbTeachers.size());
                return dbTeachers;
            }
        }

        List<Teacher> dbTeachers = findMatchingTeachersInDatabase(courseName, currentSemester);

        if (dbTeachers != null && !dbTeachers.isEmpty()) {
            System.out.println("数据库中已存在当前学期该课程教师，数量: " + dbTeachers.size());
            TEACHER_FETCH_CACHE.put(cacheKey, LocalDateTime.now());
            return dbTeachers;
        }

        System.out.println("数据库中不存在当前学期该课程教师，开始从网页获取");

        List<Teacher> webTeachers = fetchTeachersFromWebByCourseNameRule(currentSemester, courseName);
        System.out.println("从网页获取到教师数量: " + webTeachers.size());

        List<Teacher> result = new ArrayList<>();
        Set<String> uniqueKeys = new HashSet<>();

        for (Teacher webTeacher : webTeachers) {
            String uniqueKey = webTeacher.getTeacherName() + "_" +
                    webTeacher.getCourseName() + "_" +
                    webTeacher.getSemester() + "_" +
                    (webTeacher.getTeachingClass() != null ? webTeacher.getTeachingClass() : "");

            if (uniqueKeys.contains(uniqueKey)) {
                System.out.println("跳过重复教师: " + uniqueKey);
                continue;
            }
            uniqueKeys.add(uniqueKey);

            Teacher existing = findExistingTeacherRecord(webTeacher);

            if (existing == null) {
                webTeacher.setCreateTime(LocalDateTime.now());
                webTeacher.setUpdateTime(LocalDateTime.now());
                webTeacher.setStatus("PENDING");

                if (webTeacher.getPreferred() != null && webTeacher.getPreferred().length() > 255) {
                    webTeacher.setPreferred(webTeacher.getPreferred().substring(0, 255));
                }
                if (webTeacher.getTimeLocation() != null && webTeacher.getTimeLocation().length() > 255) {
                    webTeacher.setTimeLocation(webTeacher.getTimeLocation().substring(0, 255));
                }

                teacherMapper.insert(webTeacher);
                System.out.println("插入教师，ID: " + webTeacher.getId() + ", 姓名: " + webTeacher.getTeacherName());
                result.add(webTeacher);
            } else {
                System.out.println("教师已存在，ID: " + existing.getId() + ", 姓名: " + existing.getTeacherName());
                result.add(existing);
            }
        }

        TEACHER_FETCH_CACHE.put(cacheKey, LocalDateTime.now());

        System.out.println("=== 获取教师完成，返回数量: " + result.size() + " ===");
        return result;
    }

    public List<UserVO> getTeachersByCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }

        LambdaQueryWrapper<Teacher> teacherWrapper = new LambdaQueryWrapper<>();
        teacherWrapper.eq(Teacher::getCourseName, course.getCourseName());
        List<Teacher> dbTeachers = teacherMapper.selectList(teacherWrapper);

        Set<String> existingNames = new HashSet<>();
        LambdaQueryWrapper<User> existingWrapper = new LambdaQueryWrapper<>();
        existingWrapper.eq(User::getRole, "TEACHER");
        List<User> existingTeachers = userMapper.selectList(existingWrapper);
        for (User user : existingTeachers) {
            existingNames.add(user.getRealName());
        }

        List<UserVO> result = new ArrayList<>();
        for (Teacher dbTeacher : dbTeachers) {
            UserVO vo = new UserVO();
            vo.setId(dbTeacher.getId());
            vo.setUsername(dbTeacher.getTeacherName());
            vo.setRealName(dbTeacher.getTeacherName());
            vo.setTitle(dbTeacher.getTitle());
            vo.setDepartment(dbTeacher.getDepartment());
            vo.setRole("TEACHER");
            vo.setExists(existingNames.contains(dbTeacher.getTeacherName()));
            result.add(vo);
        }

        return result;
    }

    @Transactional
    public List<UserVO> saveTeachersToUserTable(List<Long> teacherIds) {
        System.out.println("=== 开始保存教师到user表 ===");
        System.out.println("教师ID列表: " + teacherIds);
        
        List<UserVO> result = new ArrayList<>();
        
        for (Long teacherId : teacherIds) {
            Teacher teacherEntity = teacherMapper.selectById(teacherId);
            if (teacherEntity == null) {
                System.out.println("教师不存在，teacherId: " + teacherId);
                continue;
            }

            List<User> teachers = resolveTeacherUsersForTeacherRecord(teacherEntity, null);
            if (teachers.isEmpty()) {
                continue;
            }

            UserVO vo = new UserVO();
            vo.setId(teachers.get(0).getId());
            vo.setTeacherId(teacherId);
            vo.setUsername(teachers.get(0).getUsername());
            vo.setRealName(joinTeacherNames(teachers));
            vo.setWorkId(joinTeacherWorkIds(teachers));
            vo.setTitle(teacherEntity.getTitle());
            vo.setDepartment(teacherEntity.getDepartment());
            vo.setRole("TEACHER");
            vo.setExists(true);
            vo.setTeachingClass(teacherEntity.getTeachingClass());
            vo.setCourseName(teacherEntity.getCourseName());
            vo.setParticipantUserIds(teachers.stream().map(User::getId).collect(Collectors.toList()));
            vo.setParticipantTeacherNames(joinTeacherNames(teachers));
            vo.setParticipantWorkIds(joinTeacherWorkIds(teachers));
            System.out.println("返回UserVO - ID: " + vo.getId() + ", RealName: " + vo.getRealName() + ", TeachingClass: " + vo.getTeachingClass());
            result.add(vo);
        }
        
        System.out.println("=== 保存教师完成，返回数量: " + result.size() + " ===");
        return result;
    }

    public Map<String, Object> checkTeacherByName(String teacherName) {
        Map<String, Object> result = new HashMap<>();

        if (teacherName == null || teacherName.trim().isEmpty()) {
            result.put("exists", false);
            result.put("message", "教师姓名不能为空");
            return result;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, "TEACHER")
                .eq(User::getRealName, teacherName);
        User teacher = userMapper.selectOne(wrapper);

        if (teacher != null) {
            result.put("exists", true);
            result.put("teacherName", teacher.getRealName());
            result.put("workId", teacher.getWorkId());
            result.put("message", "教师已存在，工号：" + teacher.getWorkId());
        } else {
            result.put("exists", false);
            result.put("teacherName", teacherName);
            result.put("message", "教师不存在，将自动创建新用户");
        }

        return result;
    }

    @Transactional
    public UserVO manualAddTask(String courseName, String teachingClass, String teacherName, String workId) {
        System.out.println("=== 开始手动添加任务 ===");
        System.out.println("课程名称: " + courseName);
        System.out.println("教学班号: " + teachingClass);
        System.out.println("教师姓名: " + teacherName);
        System.out.println("工号: " + (workId != null ? workId : "自动生成"));

        if (teacherName == null || teacherName.trim().isEmpty()) {
            throw new RuntimeException("教师姓名不能为空");
        }

        User teacher;
        boolean isNewUser = false;
        String message;
        String finalWorkId;

        if (workId != null && !workId.trim().isEmpty()) {
            // 用户输入了工号，按工号查询
            LambdaQueryWrapper<User> teacherQuery = new LambdaQueryWrapper<>();
            teacherQuery.eq(User::getRole, "TEACHER")
                    .eq(User::getWorkId, workId);
            User existingTeacher = userMapper.selectOne(teacherQuery);

            if (existingTeacher != null) {
                // 工号已存在，检查姓名是否一致
                if (!existingTeacher.getRealName().equals(teacherName)) {
                    throw new RuntimeException("工号 " + workId + " 对应的教师姓名为 "
                            + existingTeacher.getRealName() + "，与输入的姓名不一致，请确认");
                }
                teacher = existingTeacher;
                isNewUser = false;
                finalWorkId = workId;
                message = "使用已存在的教师用户：" + teacher.getRealName() + "（工号：" + workId + "）";
                System.out.println("使用已存在的教师用户，user.id: " + teacher.getId() + ", 姓名: " + teacher.getRealName());
            } else {
                // 工号不存在，检查工号是否被其他角色占用
                LambdaQueryWrapper<User> workIdQuery = new LambdaQueryWrapper<>();
                workIdQuery.eq(User::getWorkId, workId);
                if (userMapper.selectCount(workIdQuery) > 0) {
                    throw new RuntimeException("工号 " + workId + " 已被其他用户占用，请使用其他工号");
                }
                // 使用用户输入的工号创建新用户
                teacher = new User();
                teacher.setUsername(workId);
                teacher.setWorkId(workId);
                teacher.setRealName(teacherName);
                teacher.setEmail(generateEmail(teacherName));
                teacher.setPhone(generatePhone());
                teacher.setRole("TEACHER");
                teacher.setStatus(1);
                teacher.setPassword(passwordEncoder.encode("123456"));
                userMapper.insert(teacher);
                isNewUser = true;
                finalWorkId = workId;
                message = "新建教师用户成功：" + teacherName + "（工号：" + workId + "），默认密码：123456";
                System.out.println("新建教师用户，user.id: " + teacher.getId() + ", 姓名: " + teacher.getRealName());
            }
        } else {
            // 未输入工号，按姓名查询
            LambdaQueryWrapper<User> nameQuery = new LambdaQueryWrapper<>();
            nameQuery.eq(User::getRole, "TEACHER")
                    .eq(User::getRealName, teacherName);
            User existingTeacher = userMapper.selectOne(nameQuery);

            if (existingTeacher != null) {
                // 姓名已存在，使用该教师
                teacher = existingTeacher;
                isNewUser = false;
                finalWorkId = teacher.getWorkId();
                message = "使用已存在的教师用户：" + teacher.getRealName() + "（工号：" + teacher.getWorkId() + "）";
                System.out.println("使用已存在的教师用户，user.id: " + teacher.getId() + ", 姓名: " + teacher.getRealName());
            } else {
                // 姓名不存在，自动生成工号创建新用户
                finalWorkId = generateUniqueWorkId(teacherName);
                teacher = new User();
                teacher.setUsername(finalWorkId);
                teacher.setWorkId(finalWorkId);
                teacher.setRealName(teacherName);
                teacher.setEmail(generateEmail(teacherName));
                teacher.setPhone(generatePhone());
                teacher.setRole("TEACHER");
                teacher.setStatus(1);
                teacher.setPassword(passwordEncoder.encode("123456"));
                userMapper.insert(teacher);
                isNewUser = true;
                message = "新建教师用户成功：" + teacherName + "（工号：" + finalWorkId + "），默认密码：123456";
                System.out.println("新建教师用户，user.id: " + teacher.getId() + ", 姓名: " + teacher.getRealName() + ", 工号: " + finalWorkId);
            }
        }

        Teacher teacherEntity = new Teacher();
        teacherEntity.setCourseName(courseName);
        teacherEntity.setTeachingClass(teachingClass);
        teacherEntity.setTeacherName(teacher.getRealName());
        teacherEntity.setDepartment(teacher.getDepartment() != null ? teacher.getDepartment() : "未知院系");
        teacherEntity.setTitle(teacher.getTitle() != null ? teacher.getTitle() : "教师");
        teacherEntity.setSemester("1");
        teacherEntity.setStatus("0");
        teacherEntity.setCreateTime(LocalDateTime.now());
        teacherEntity.setUpdateTime(LocalDateTime.now());
        teacherMapper.insert(teacherEntity);
        System.out.println("创建teacher记录成功，teacher.id: " + teacherEntity.getId() + ", 课程: " + teacherEntity.getCourseName());

        UserVO vo = new UserVO();
        vo.setId(teacher.getId());
        vo.setUsername(teacher.getUsername());
        vo.setRealName(teacher.getRealName());
        vo.setWorkId(teacher.getWorkId());
        vo.setTitle(teacher.getTitle());
        vo.setDepartment(teacher.getDepartment());
        vo.setRole("TEACHER");
        vo.setExists(true);
        vo.setIsNewUser(isNewUser);
        vo.setMessage(message);
        vo.setTeacherId(teacherEntity.getId());

        System.out.println("=== 手动添加任务完成 ===");
        System.out.println("返回UserVO - ID: " + vo.getId() + ", Username: " + vo.getUsername()
                + ", RealName: " + vo.getRealName() + ", TeacherId: " + vo.getTeacherId()
                + ", IsNewUser: " + vo.getIsNewUser());
        return vo;
    }

    @Transactional
    public void autoAddTeachers(List<User> teachers) {
        for (User teacher : teachers) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getRole, "TEACHER");
            wrapper.eq(User::getRealName, teacher.getRealName());
            User existing = userMapper.selectOne(wrapper);

            if (existing == null) {
                String workId = generateWorkId(teacher.getRealName());
                String username = teacher.getUsername();
                if (username == null || username.trim().isEmpty()) {
                    username = "teacher_" + workId;
                }

                LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
                usernameWrapper.eq(User::getUsername, username);
                if (userMapper.selectCount(usernameWrapper) > 0) {
                    username = username + "_" + System.currentTimeMillis();
                }

                LambdaQueryWrapper<User> workIdWrapper = new LambdaQueryWrapper<>();
                workIdWrapper.eq(User::getWorkId, workId);
                if (userMapper.selectCount(workIdWrapper) > 0) {
                    workId = workId + System.currentTimeMillis();
                }

                User newUser = new User();
                newUser.setUsername(username);
                newUser.setWorkId(workId);
                newUser.setRealName(teacher.getRealName());
                newUser.setEmail(generateEmail(teacher.getRealName()));
                newUser.setPhone(generatePhone());
                newUser.setPassword(passwordEncoder.encode("123456"));
                newUser.setRole("TEACHER");
                newUser.setDepartment(teacher.getDepartment() != null ? teacher.getDepartment() : "未知院系");
                newUser.setTitle(teacher.getTitle() != null ? teacher.getTitle() : "教师");
                newUser.setStatus(1);
                newUser.setCreateTime(LocalDateTime.now());
                newUser.setUpdateTime(LocalDateTime.now());
                userMapper.insert(newUser);
            }
        }
    }

    private String generateWorkId(String realName) {
        String pinyin = convertToPinyin(realName);
        if (pinyin == null || pinyin.isEmpty()) {
            return String.valueOf(System.currentTimeMillis());
        }
        return pinyin.toUpperCase();
    }

    private String generateUniqueWorkId(String realName) {
        String baseWorkId = generateWorkId(realName);
        String workId = baseWorkId;
        int suffix = 1;

        while (true) {
            LambdaQueryWrapper<User> workIdWrapper = new LambdaQueryWrapper<>();
            workIdWrapper.eq(User::getWorkId, workId);
            if (userMapper.selectCount(workIdWrapper) == 0) {
                break;
            }
            workId = baseWorkId + suffix;
            suffix++;
        }

        return workId;
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
        String randomSuffix = String.valueOf(System.currentTimeMillis()).substring(8);
        return pinyin.toLowerCase() + randomSuffix + "@swjtu.edu.cn";
    }

    private String generatePhone() {
        Random random = new Random();
        return "138" + String.format("%08d", random.nextInt(100000000));
    }

    private List<Teacher> filterTeachersByGrade(List<Teacher> teachers, String grade) {
        if (grade == null || grade.trim().isEmpty()) {
            return teachers;
        }
        
        List<Teacher> filtered = new ArrayList<>();
        for (Teacher teacher : teachers) {
            String preferred = teacher.getPreferred();
            if (preferred != null && !preferred.trim().isEmpty()) {
                String yearFromPreferred = extractFirstFourDigits(preferred);
                if (grade.equals(yearFromPreferred)) {
                    filtered.add(teacher);
                }
            }
        }
        return filtered;
    }

    private List<Teacher> filterTeachersByGradeAndMajor(List<Teacher> teachers, String grade, String major) {
        System.out.println("=== 开始筛选教师 ===");
        System.out.println("教师总数: " + teachers.size());
        System.out.println("年级: " + grade);
        System.out.println("专业: " + major);

        if (grade == null || grade.trim().isEmpty()) {
            if (major == null || major.trim().isEmpty()) {
                System.out.println("年级和专业都为空，返回所有教师");
                return teachers;
            }
            return filterByMajor(teachers, major);
        }

        if (major == null || major.trim().isEmpty()) {
            System.out.println("专业为空，只按年级筛选");
            return filterByGrade(teachers, grade);
        }

        List<String> matchKeywords = majorMappingConfig.getMatchKeywords(major);

        System.out.println("专业映射配置获取的关键词: " + matchKeywords);
        System.out.println("配置的所有映射: " + majorMappingConfig.getMappings());
        System.out.println("matchKeywords是否为空: " + (matchKeywords == null || matchKeywords.isEmpty()));

        if (matchKeywords == null || matchKeywords.isEmpty()) {
            System.out.println("专业 " + major + " 没有配置匹配关键词，返回空列表");
            return new ArrayList<>();
        }

        List<Teacher> filtered = new ArrayList<>();
        for (Teacher teacher : teachers) {
            String preferred = teacher.getPreferred();
            String department = teacher.getDepartment();
            System.out.println("检查教师: " + teacher.getTeacherName() + ", preferred: " + preferred + ", department: " + department);

            if (preferred != null && !preferred.trim().isEmpty()) {
                String yearFromPreferred = extractFirstFourDigits(preferred);
                boolean gradeMatch = grade.equals(yearFromPreferred);

                System.out.println("  提取的年级: " + yearFromPreferred + ", 年级匹配: " + gradeMatch);

                // 专业匹配：preferred 包含关键词 或 department 包含关键词
                boolean majorMatch = false;
                System.out.println("  开始匹配专业关键词，共 " + matchKeywords.size() + " 个关键词");
                for (String keyword : matchKeywords) {
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        if ((preferred != null && preferred.contains(keyword)) ||
                                (department != null && department.contains(keyword))) {
                            majorMatch = true;
                            System.out.println("    关键词 '" + keyword + "' 匹配成功（preferred或department）");
                            break;
                        }
                    }
                }

                System.out.println("  专业匹配: " + majorMatch + ", 最终匹配: " + (gradeMatch && majorMatch));

                if (gradeMatch && majorMatch) {
                    filtered.add(teacher);
                }
            }
        }

        System.out.println("=== 筛选完成，返回 " + filtered.size() + " 个教师 ===");
        return filtered;
    }

    private List<Teacher> filterByGrade(List<Teacher> teachers, String grade) {
        List<Teacher> filtered = new ArrayList<>();
        for (Teacher teacher : teachers) {
            String preferred = teacher.getPreferred();
            if (preferred != null && !preferred.trim().isEmpty()) {
                String yearFromPreferred = extractFirstFourDigits(preferred);
                if (grade.equals(yearFromPreferred)) {
                    filtered.add(teacher);
                }
            }
        }
        return filtered;
    }

    private List<Teacher> filterByMajor(List<Teacher> teachers, String major) {
        List<String> matchKeywords = majorMappingConfig.getMatchKeywords(major);
        if (matchKeywords.isEmpty()) {
            return teachers;
        }

        List<Teacher> filtered = new ArrayList<>();
        for (Teacher teacher : teachers) {
            String preferred = teacher.getPreferred();
            if (preferred != null && !preferred.trim().isEmpty()) {
                for (String keyword : matchKeywords) {
                    if (keyword != null && !keyword.trim().isEmpty() && preferred.contains(keyword)) {
                        filtered.add(teacher);
                        break;
                    }
                }
            }
        }
        return filtered;
    }

    private String extractFirstFourDigits(String text) {
        StringBuilder digits = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
                if (digits.length() == 4) {
                    break;
                }
            }
        }
        return digits.toString();
    }

    public List<CourseItemVO> getCoursesByGrade(String major, String grade) {
        // 从 Course 表查询该专业、该年级的所有课程
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getMajor, major)
                        .eq(Course::getGrade, grade)
        );

        List<CourseItemVO> result = new ArrayList<>();
        List<String> matchKeywords = majorMappingConfig.getMatchKeywords(major);
        System.out.println("专业 " + major + " 映射关键词: " + matchKeywords);

        for (Course course : courses) {
            int semesterIndex = getSemesterIndexFromCourse(course);
            String actualSemester = calculateActualSemester(grade, semesterIndex);
            // 转换为教务网实际学期（处理短学期）
//            String actualSemesterForWeb = convertShortSemesterToActual(actualSemester, grade);

            // 获取该课程该学期的所有教学班（使用原始课程名称和实际学期）
            List<Teacher> teachers = getOrFetchTeachers(major, course.getCourseName(), actualSemester);

            // 过滤特殊班级（茅班、利兹）
            teachers = filterSpecialClasses(teachers, major);

            // 根据年级和专业关键词筛选教学班
            List<Teacher> filtered;
            if (matchKeywords != null && !matchKeywords.isEmpty()) {
                filtered = teachers.stream()
                        .filter(t -> t.getPreferred() != null)
                        .filter(t -> {
                            boolean gradeMatch = t.getPreferred().contains(grade);
                            boolean majorMatch = matchKeywords.stream()
                                    .anyMatch(keyword -> keyword != null && !keyword.isEmpty() && t.getPreferred().contains(keyword));
                            return gradeMatch && majorMatch;
                        })
                        .collect(Collectors.toList());
            } else {
                filtered = new ArrayList<>();
            }

            if (!filtered.isEmpty()) {
                for (Teacher teacher : filtered) {
                    CourseItemVO vo = convertTeacherToItemVO(teacher);
                    vo.setType("teacher");
                    vo.setCanFile(true);
                    LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
                    taskWrapper.eq(Task::getCourseId, teacher.getId());
                    vo.setIsFiled(taskMapper.selectCount(taskWrapper) > 0);
                    result.add(vo);
                }
            } else {
                addFutureCourseItem(result, course, actualSemester);
            }
        }
        return result;
    }

    /**
     * 从专业名称中提取关键词（例如："计算机科学与技术" -> "计算机"）
     * 可根据实际专业名称特点调整
     */
    private String extractMajorKeyword(String major) {
        if (major == null) return "";
        // 简单取前两个汉字，或根据常见专业名称截取
        if (major.contains("计算机")) return "计算机";
        if (major.contains("软件")) return "软件";
        if (major.contains("通信")) return "通信";
        if (major.contains("电子")) return "电子";
        if (major.contains("土木")) return "土木";
        if (major.contains("机械")) return "机械";
        // 默认返回专业名称本身
        return major;
    }

    // 辅助方法：添加未来课程项
    private void addFutureCourseItem(List<CourseItemVO> list, Course course, String semester) {
        CourseItemVO vo = new CourseItemVO();
        vo.setId(course.getId());
        vo.setType("course");
        vo.setCourseCode(""); // 无课程代码
        vo.setSelectCode("");
        vo.setCourseName(course.getCourseName());
        vo.setCredits(0); // 学分暂缺
        vo.setSemester(semester);
        vo.setPreferred("");
        vo.setTeachingClass("");
        vo.setTeacherName("未开课");
        vo.setEnrollCount("");
        vo.setIsFiled(false);
        vo.setCanFile(true);
        list.add(vo);
    }

    public List<CourseItemVO> getCoursesBySemester(String major, String semester) {
        // 先尝试从 Teacher 表查询该专业、该学期的教学班记录
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getDepartment, major)
                .eq(Teacher::getSemester, semester);
        List<Teacher> teachers = teacherMapper.selectList(wrapper);

        // 如果本地没有数据，且不是未来学期，则从教务系统抓取
        if (teachers.isEmpty() && !isFutureSemester(semester)) {
            LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
            courseWrapper.eq(Course::getMajor, major);
            List<Course> courses = courseMapper.selectList(courseWrapper);
            for (Course course : courses) {
                List<Teacher> fetched = fetchTeachersFromWeb(course.getCourseName(), semester, null, major);
                teachers.addAll(fetched);
            }
            // 去重
            teachers = teachers.stream()
                    .collect(Collectors.collectingAndThen(
                            Collectors.toCollection(() -> new TreeSet<>(
                                    Comparator.comparing(t -> t.getTeacherName() + "_" + t.getTeachingClass()))),
                            ArrayList::new));
        }

        // 过滤特殊班级
        teachers = filterSpecialClasses(teachers, major);

        // 转换为 CourseItemVO
        List<CourseItemVO> result = new ArrayList<>();
        for (Teacher teacher : teachers) {
            CourseItemVO vo = new CourseItemVO();
            vo.setId(teacher.getId());
            vo.setType("teacher");
            vo.setCourseCode(teacher.getCourseCode());
            vo.setSelectCode(teacher.getSelectCode());
            vo.setCourseName(teacher.getCourseName());
            vo.setCredits(teacher.getCredits() != null ? teacher.getCredits().intValue() : 0);
            vo.setSemester(teacher.getSemester());
            vo.setPreferred(teacher.getPreferred());
            vo.setTeachingClass(teacher.getTeachingClass());
            vo.setTeacherName(teacher.getTeacherName());
            vo.setEnrollCount(teacher.getStatus());
            // 判断是否已备案
            LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
            taskWrapper.eq(Task::getCourseId, teacher.getId());
            long count = taskMapper.selectCount(taskWrapper);
            vo.setIsFiled(count > 0);
            vo.setCanFile(true);
            result.add(vo);
        }
        return result;
    }

    private List<CourseTeachingVO> convertToCourseTeachingVO(List<Teacher> teachers) {
        return teachers.stream().map(t -> {
            CourseTeachingVO vo = new CourseTeachingVO();
            vo.setId(t.getId());
            vo.setCourseCode(t.getCourseCode());
            vo.setSelectCode(t.getSelectCode());
            vo.setCourseName(t.getCourseName());
            vo.setCredits(t.getCredits() != null ? t.getCredits().intValue() : 0);
            vo.setSemester(t.getSemester());
            vo.setPreferred(t.getPreferred());
            vo.setTeachingClass(t.getTeachingClass());
            vo.setTeacherName(t.getTeacherName());
            vo.setEnrollCount(t.getStatus());

            LambdaQueryWrapper<Task> taskWrapper = new LambdaQueryWrapper<>();
            taskWrapper.eq(Task::getCourseId, t.getId());
            long taskCount = taskMapper.selectCount(taskWrapper);
            vo.setIsFiled(taskCount > 0);

            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 生成备案任务并返回教师账号清单下载链接
     * @param dto 任务配置信息
     * @return 包含两个下载URL的Map
     * @throws IOException Excel文件生成异常
     */
    @Transactional
    public Map<String, Object> generateTasks(GenerateTaskDTO dto) throws IOException {
        System.out.println("=== generateTasks ===");
        System.out.println("taskName: " + dto.getTaskName());
        System.out.println("startTime: " + dto.getStartTime());
        System.out.println("endTime: " + dto.getEndTime());
        System.out.println("assessorId: " + dto.getAssessorId());
        System.out.println("teacherIds: " + dto.getTeacherIds());
        System.out.println("courseIds: " + dto.getCourseIds());
        System.out.println("items: " + dto.getItems());
        System.out.println("futureCourseTeacher: " + dto.getFutureCourseTeacher());

        List<Long> teacherIds = dto.getTeacherIds() != null ? dto.getTeacherIds() : new ArrayList<>();
        List<Long> courseIds = dto.getCourseIds() != null ? dto.getCourseIds() : new ArrayList<>();

        if (teacherIds.isEmpty() && courseIds.isEmpty()) {
            throw new RuntimeException("请至少选择一个教学班或课程");
        }

        // 构建备案项目描述文本
        List<TaskItemUtils.ItemConfig> requiredItems = TaskItemUtils.parseRequiredItems(
                dto.getItems() == null ? null : String.join(",", dto.getItems()));
        String materialRequirements = requiredItems.isEmpty()
                ? null
                : requiredItems.stream().map(TaskItemUtils.ItemConfig::getCode).collect(Collectors.joining(","));

        // 获取现有教师姓名集合
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(User::getRole, "TEACHER");
        List<User> allTeachers = userMapper.selectList(userWrapper);
        Set<String> existingTeacherNames = allTeachers.stream()
                .map(User::getRealName)
                .collect(Collectors.toSet());

        // 初始化清单
        List<Map<String, String>> newTeacherInfo = new ArrayList<>();
        List<Map<String, String>> existingTeacherInfo = new ArrayList<>();
        Set<String> accountSheetKeys = new HashSet<>();

        // 处理未来课程教师（如果有）
        Long futureTeacherId = null;
        if (dto.getFutureCourseTeacher() != null && !dto.getFutureCourseTeacher().trim().isEmpty()) {
            futureTeacherId = getOrCreateTeacherUser(dto.getFutureCourseTeacher());
            if (futureTeacherId != null) {
                User futureTeacher = userMapper.selectById(futureTeacherId);
                if (futureTeacher != null) {
                    boolean isNew = !existingTeacherNames.contains(futureTeacher.getRealName());
                    Map<String, String> info = new HashMap<>();
                    info.put("name", futureTeacher.getRealName());
                    info.put("workId", futureTeacher.getWorkId());
                    if (isNew) {
                        info.put("password", "123456");
                        newTeacherInfo.add(info);
                    } else {
                        existingTeacherInfo.add(info);
                    }
                }
            }
        }

        // 处理教学班教师
        List<UserVO> teacherUserVOs = new ArrayList<>();
        if (!teacherIds.isEmpty()) {
            teacherUserVOs = saveTeachersToUserTable(teacherIds);
        }

        // 构建教学班任务并收集教师清单
        BatchTaskDTO batchTaskDTO = new BatchTaskDTO();
        List<BatchTaskDTO.TaskItem> taskItems = new ArrayList<>();

        for (UserVO vo : teacherUserVOs) {
            List<Long> participantIds = vo.getParticipantUserIds() == null || vo.getParticipantUserIds().isEmpty()
                    ? List.of(vo.getId())
                    : vo.getParticipantUserIds();
            for (Long participantId : participantIds) {
                User participant = userMapper.selectById(participantId);
                if (participant == null) {
                    continue;
                }
                String accountKey = participant.getWorkId();
                if (!accountSheetKeys.add(accountKey)) {
                    continue;
                }
                boolean isNew = !existingTeacherNames.contains(participant.getRealName());
                Map<String, String> info = new HashMap<>();
                info.put("name", participant.getRealName());
                info.put("workId", participant.getWorkId());
                if (isNew) {
                    info.put("password", "123456");
                    newTeacherInfo.add(info);
                } else {
                    existingTeacherInfo.add(info);
                }
            }

            BatchTaskDTO.TaskItem item = new BatchTaskDTO.TaskItem();
            item.setCourseId(vo.getTeacherId());
            item.setTeacherId(vo.getId());
            item.setAssessorId(dto.getAssessorId());
            item.setDeadline(LocalDateTime.parse(dto.getEndTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            item.setDescription("任务名称：" + dto.getTaskName());
            item.setMaterialRequirements(materialRequirements);
            item.setIsManual(false);
            taskItems.add(item);
        }

        if (!taskItems.isEmpty()) {
            batchTaskDTO.setTasks(taskItems);
            batchCreateTasks(batchTaskDTO);
        }

        // 处理未来课程任务
        if (!courseIds.isEmpty()) {
            for (Long courseId : courseIds) {
                Course course = courseMapper.selectById(courseId);
                if (course == null) {
                    throw new RuntimeException("课程不存在，ID: " + courseId);
                }
                Task task = new Task();
                task.setCourseId(courseId);
                task.setTeacherId(futureTeacherId);
                task.setAssessorId(dto.getAssessorId());
                task.setDeadline(LocalDateTime.parse(dto.getEndTime(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                task.setDescription("任务名称：" + dto.getTaskName());
                task.setMaterialRequirements(materialRequirements);
                task.setStatus("PENDING_UPLOAD");
                task.setCreateTime(LocalDateTime.now());
                task.setUpdateTime(LocalDateTime.now());
                taskMapper.insert(task);

                if (futureTeacherId != null) {
                    User teacher = userMapper.selectById(futureTeacherId);
                    if (teacher != null) {
                        String teacherNotificationTitle = "新任务通知";
                        String teacherNotificationContent = String.format(
                                "您收到了一个新的备案任务。课程：%s（未来课程），截止日期：%s。请及时完成材料上传。",
                                course.getCourseName(),
                                dto.getEndTime()
                        );
                        notificationService.createNotification(
                                teacher.getId(),
                                task.getId(),
                                "NEW_TASK",
                                teacherNotificationTitle,
                                teacherNotificationContent
                        );
                        // 发送邮件给未来课程教师
                        if (teacher.getEmail() != null && !teacher.getEmail().isEmpty()) {
                            String subject = "【专业认证备案】新任务通知 - " + course.getCourseName();
                            String emailContent = buildTeacherEmailContent(
                                    course.getCourseName(),
                                    "未开课",  // 未来课程无教学班号
                                    dto.getEndTime()
                            );
                            emailService.sendHtmlMail(teacher.getEmail(), subject, emailContent);
                        }

                        // 发送邮件给审核员（如果有）
                        if (dto.getAssessorId() != null) {
                            User assessor = userMapper.selectById(dto.getAssessorId());
                            if (assessor != null && assessor.getEmail() != null && !assessor.getEmail().isEmpty()) {
                                String subject = "【专业认证备案】新审核任务通知 - " + course.getCourseName();
                                String emailContent = buildAssessorEmailContent(
                                        course.getCourseName(),
                                        "未开课",
                                        teacher.getRealName(),
                                        dto.getEndTime()
                                );
                                emailService.sendHtmlMail(assessor.getEmail(), subject, emailContent);
                            }
                        }
                    }
                }
            }
        }

        // 生成 Excel 文件
        String newTeachersFile = generateExcel("新分配账号的教师清单", newTeacherInfo, true,
                dto.getTaskName(), dto.getStartTime(), dto.getEndTime(), "新教师");
        String existingTeachersFile = generateExcel("已有账号教师清单", existingTeacherInfo, false,
                dto.getTaskName(), dto.getStartTime(), dto.getEndTime(), "已有教师");

        // 返回结果，包含下载链接和已创建任务的课程ID
        Map<String, Object> result = new HashMap<>();
        result.put("newTeachers", "/api/admin/download/" + newTeachersFile);
        result.put("existingTeachers", "/api/admin/download/" + existingTeachersFile);
        // 收集本次创建任务的课程ID（教学班ID + 未来课程ID）
        List<Long> createdCourseIds = new ArrayList<>();
        createdCourseIds.addAll(teacherIds);
        createdCourseIds.addAll(courseIds);
        result.put("createdCourseIds", createdCourseIds);
        return result;
    }

    /**
     * 生成 Excel 文件，返回文件名
     * @param title 工作表名称
     * @param data 数据列表，每个 Map 包含 name, workId, 若包含密码则需 password
     * @param includePassword 是否包含密码列
     * @return 生成的文件名（含路径相对值）
     */
    private String generateExcel(String title, List<Map<String, String>> data, boolean includePassword,
                                 String taskName, String startTime, String endTime, String suffix) throws IOException {
        // 确保下载目录存在
        Path dir = Paths.get(downloadPath);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        // 格式化日期：将 ISO 日期时间转为 yyyyMMdd
        String startDate = LocalDateTime.parse(startTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String endDate = LocalDateTime.parse(endTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // 构建基础文件名，替换非法字符
        String baseName = taskName + "-" + startDate + "-" + endDate + "-" + suffix;
        String safeName = baseName.replaceAll("[\\\\/:*?\"<>|]", "_");
        String filename = safeName + ".xlsx";

        Path filePath = dir.resolve(filename);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title);

            // 创建表头
            String[] headers = includePassword ?
                    new String[]{"姓名", "工号", "密码"} :
                    new String[]{"姓名", "工号"};
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 填充数据
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, String> item = data.get(i);
                row.createCell(0).setCellValue(item.get("name"));
                row.createCell(1).setCellValue(item.get("workId"));
                if (includePassword) {
                    row.createCell(2).setCellValue(item.get("password"));
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                workbook.write(fos);
            }
        }

        return filename;
    }

    public List<String> getAllDepartmentsFromCourse() {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Course::getMajor);
        wrapper.isNotNull(Course::getMajor);
        wrapper.groupBy(Course::getMajor);
        wrapper.orderByAsc(Course::getMajor);
        List<Course> courses = courseMapper.selectList(wrapper);
        return courses.stream()
                .map(Course::getMajor)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String calculateActualSemester(String grade, int semesterIndex) {
        int baseYear = Integer.parseInt(grade);
        int yearOffset = (semesterIndex - 1) / 2;
        int startYear = baseYear + yearOffset;
        int endYear = startYear + 1;
        int semesterFlag = (semesterIndex % 2 == 1) ? 1 : 2;
        return startYear + "-" + endYear + "-" + semesterFlag;
    }

    private boolean isSemesterPassed(String semester) {
        // 简单判断：如果当前日期晚于该学期结束日期，则认为已过
        if (!semester.matches("\\d{4}-\\d{4}-[12]")) return false;
        String[] parts = semester.split("-");
        int startYear = Integer.parseInt(parts[0]);
        int endYear = Integer.parseInt(parts[1]);
        int flag = Integer.parseInt(parts[2]);
        LocalDate now = LocalDate.now();
        // 假设第一学期结束于次年1月31日，第二学期结束于当年7月31日
        LocalDate endDate;
        if (flag == 1) {
            endDate = LocalDate.of(endYear, 1, 31);
        } else {
            endDate = LocalDate.of(endYear, 7, 31);
        }
        return now.isAfter(endDate);
    }

    private List<Teacher> getOrFetchTeachers(String major, String courseName, String semester) {
        List<Teacher> teachers = findMatchingTeachersInDatabase(courseName, semester);
        if (teachers.isEmpty()) {
            if (isFutureSemester(semester)) {
                return new ArrayList<>();
            }
            teachers = fetchTeachersFromWebByCourseNameRule(semester, courseName);
            for (Teacher t : teachers) {
                Teacher existing = findExistingTeacherRecord(t);
                if (existing == null) {
                    t.setCreateTime(LocalDateTime.now());
                    t.setUpdateTime(LocalDateTime.now());
                    teacherMapper.insert(t);
                }
            }
            teachers = findMatchingTeachersInDatabase(courseName, semester);
        }
        teachers = filterSpecialClasses(teachers, major);
        return teachers;
    }

    private List<Teacher> findMatchingTeachersInDatabase(String courseName, String semester) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getSemester, semester);
        List<Teacher> teachers = teacherMapper.selectList(wrapper);
        return teachers.stream()
                .filter(teacher -> CourseNameUtils.matchesCourseName(courseName, teacher.getCourseName()))
                .collect(Collectors.toList());
    }

    private List<Teacher> fetchTeachersFromWebByCourseNameRule(String semester, String courseName) {
        for (String queryCourseName : CourseNameUtils.buildQueryCourseNames(courseName)) {
            System.out.println("按课程名抓取教务数据: 原始课程=" + courseName + "，查询课程=" + queryCourseName);
            List<Teacher> teachers = GetTeacherList.getTeachersByAndSemesterCourseName(semester, queryCourseName).stream()
                    .filter(teacher -> CourseNameUtils.matchesCourseName(courseName, teacher.getCourseName()))
                    .collect(Collectors.toList());
            if (!teachers.isEmpty()) {
                return teachers;
            }
        }
        return new ArrayList<>();
    }

    private Teacher findExistingTeacherRecord(Teacher webTeacher) {
        LambdaQueryWrapper<Teacher> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Teacher::getTeacherName, webTeacher.getTeacherName());
        wrapper.eq(Teacher::getSemester, webTeacher.getSemester());
        wrapper.eq(Teacher::getTeachingClass, webTeacher.getTeachingClass());
        List<Teacher> existingList = teacherMapper.selectList(wrapper);
        for (Teacher existing : existingList) {
            if (CourseNameUtils.matchesCourseName(webTeacher.getCourseName(), existing.getCourseName())) {
                return existing;
            }
        }
        return null;
    }

    private boolean isFutureSemester(String semester) {
        if (semester == null || !semester.matches("\\d{4}-\\d{4}-[12]")) {
            return false;
        }
        String[] parts = semester.split("-");
        int startYear = Integer.parseInt(parts[0]);
        int endYear = startYear + 1;
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        return endYear > currentYear;
    }

    private CourseItemVO convertTeacherToItemVO(Teacher teacher) {
        CourseItemVO vo = new CourseItemVO();
        vo.setId(teacher.getId());
        vo.setType("teacher");
        vo.setCourseCode(teacher.getCourseCode());
        vo.setSelectCode(teacher.getSelectCode());
        vo.setCourseName(teacher.getCourseName());
        vo.setCredits(teacher.getCredits() != null ? teacher.getCredits().intValue() : 0);
        vo.setSemester(teacher.getSemester());
        vo.setPreferred(teacher.getPreferred());
        vo.setTeachingClass(teacher.getTeachingClass());
        vo.setTeacherName(teacher.getTeacherName());
        vo.setEnrollCount(teacher.getStatus());
        return vo;
    }
    private int getSemesterIndexFromCourse(Course course) {
        String semesterStr = course.getSemester();
        if (semesterStr == null || semesterStr.trim().isEmpty()) {
            return 1; // 默认第一学期
        }
        semesterStr = semesterStr.trim();

        // 处理短学期：短1 → 2, 短2 → 4, 短3 → 6, 短4 → 8
        if (semesterStr.startsWith("短")) {
            Pattern p = Pattern.compile("短(\\d+)学期");
            Matcher m = p.matcher(semesterStr);
            if (m.find()) {
                int shortNum = Integer.parseInt(m.group(1));
                int actualSemesterNum = shortNum * 2;
                // 限制在1-8之间
                return Math.max(1, Math.min(actualSemesterNum, 8));
            }
        }

        // 尝试提取数字（如"1"、"2"等）
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(semesterStr);
        if (matcher.find()) {
            int index = Integer.parseInt(matcher.group());
            return Math.max(1, Math.min(index, 8));
        }
        return 1;
    }

    public List<String> getCoursePlanGrades(String major) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Course::getMajor, major);
        wrapper.select(Course::getGrade);
        wrapper.groupBy(Course::getGrade);
        List<Course> courses = courseMapper.selectList(wrapper);
        return courses.stream()
                .map(Course::getGrade)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional
    public void copyCoursePlan(String major, String sourceGrade, String targetGrade) {
        System.out.println("=== 开始复制培养方案 ===");
        System.out.println("专业: " + major);
        System.out.println("源年级: " + sourceGrade);
        System.out.println("目标年级: " + targetGrade);

        // 1. 检查目标年级是否已存在课程
        LambdaQueryWrapper<Course> targetCheck = new LambdaQueryWrapper<>();
        targetCheck.eq(Course::getMajor, major).eq(Course::getGrade, targetGrade);
        if (courseMapper.selectCount(targetCheck) > 0) {
            throw new RuntimeException("目标年级已存在课程，无法复制，请先删除或确认");
        }

        // 2. 查询源年级的课程
        LambdaQueryWrapper<Course> sourceWrapper = new LambdaQueryWrapper<>();
        sourceWrapper.eq(Course::getMajor, major).eq(Course::getGrade, sourceGrade);
        List<Course> sourceCourses = courseMapper.selectList(sourceWrapper);
        System.out.println("源年级课程数量: " + sourceCourses.size());
        if (sourceCourses.isEmpty()) {
            throw new RuntimeException("源年级无课程数据");
        }

        int insertedCount = 0;
        // 3. 复制并保持原学期不变
        for (Course source : sourceCourses) {
            Course newCourse = new Course();
            BeanUtils.copyProperties(source, newCourse, "id", "createTime", "updateTime");
            newCourse.setGrade(targetGrade);
            // 保持原学期，不进行转换
            newCourse.setSemester(source.getSemester());
            newCourse.setCreateTime(LocalDateTime.now());
            newCourse.setUpdateTime(LocalDateTime.now());
            try {
                courseMapper.insert(newCourse);
                insertedCount++;
                System.out.println("插入课程: " + newCourse.getCourseName() + " 学期: " + newCourse.getSemester());
            } catch (Exception e) {
                System.err.println("插入失败: " + e.getMessage());
                throw new RuntimeException("插入课程失败: " + newCourse.getCourseName(), e);
            }
        }
        System.out.println("=== 复制完成，共插入 " + insertedCount + " 条课程 ===");
    }

    /**
     * 审核任务分配
     * @param reviewAssignmentDTO 审核任务分配DTO
     */
    public void assignReviewTasks(com.swjtu.certification.dto.ReviewAssignmentDTO reviewAssignmentDTO) {
        // 解析截止日期
        LocalDateTime deadline;
        try {
            deadline = LocalDateTime.parse(reviewAssignmentDTO.getDeadline() + "T23:59:59");
        } catch (Exception e) {
            throw new RuntimeException("截止日期格式错误，应为：yyyy-MM-dd");
        }

        Map<Long, List<String>> assessorEmailSummaries = new LinkedHashMap<>();

        // 为每个任务分配审核人
        for (Long taskId : reviewAssignmentDTO.getTaskIds()) {
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                System.err.println("任务不存在: " + taskId);
                continue;
            }

            Set<String> availableProjectCodes = reviewWorkflowService.getAvailableReviewProjects(task).stream()
                    .map(ReviewProjectVO::getCode)
                    .collect(Collectors.toSet());
            List<String> selectedProjectCodes = reviewAssignmentDTO.getReviewProjects().stream()
                    .filter(availableProjectCodes::contains)
                    .distinct()
                    .collect(Collectors.toList());

            if (selectedProjectCodes.isEmpty()) {
                continue;
            }

            // 为每个审核人分配任务
            for (Map.Entry<Long, List<Long>> entry : reviewAssignmentDTO.getAssessorAssignments().entrySet()) {
                Long assessorId = entry.getKey();
                List<Long> assignedTaskIds = entry.getValue();

                // 检查该任务是否分配给当前审核人
                if (!assignedTaskIds.contains(taskId)) {
                    continue;
                }

                try {
                    // 更新现有任务的审核人和截止时间
                    task.setAssessorId(assessorId);
                    task.setDeadline(deadline);
                    task.setUpdateTime(LocalDateTime.now());

                    taskMapper.updateById(task);
                    reviewWorkflowService.assignProjects(task, selectedProjectCodes, assessorId);
                    reviewWorkflowService.refreshTaskStatus(task.getId());

                    // 创建通知
                    Notification notification = new Notification();
                    notification.setUserId(assessorId);
                    notification.setTaskId(task.getId());
                    notification.setType("NEW_TASK");
                    notification.setTitle("新审核任务");
                    notification.setContent("您有新的审核任务需要处理，截止日期: " + reviewAssignmentDTO.getDeadline());
                    notification.setIsRead(0);
                    notification.setCreateTime(LocalDateTime.now());
                    notificationMapper.insert(notification);

                    TaskVO taskVO = convertToTaskVO(task);
                    String reviewProjectsText = selectedProjectCodes.stream()
                            .map(code -> {
                                String itemName = TaskItemUtils.getItemName(code);
                                return itemName != null ? code + "-" + itemName : code;
                            })
                            .collect(Collectors.joining("、"));
                    String taskSummary = String.format("课程：%s%s；教师：%s；审核项目：%s；截止日期：%s",
                            taskVO.getCourseName(),
                            taskVO.getTeachingClass() == null || taskVO.getTeachingClass().isEmpty() ? "" : "（" + taskVO.getTeachingClass() + "）",
                            taskVO.getTeacherName(),
                            reviewProjectsText,
                            reviewAssignmentDTO.getDeadline());
                    assessorEmailSummaries.computeIfAbsent(assessorId, key -> new ArrayList<>()).add(taskSummary);

                } catch (Exception e) {
                    System.err.println("分配审核任务失败: " + e.getMessage());
                    throw new RuntimeException("分配审核任务失败", e);
                }
            }
        }

        assessorEmailSummaries.forEach((assessorId, summaries) -> {
            User assessor = userMapper.selectById(assessorId);
            if (assessor != null && assessor.getEmail() != null && !assessor.getEmail().isEmpty() && !summaries.isEmpty()) {
                String subject = "【专业认证备案】审核任务分配通知";
                String content = buildReviewAssignmentEmailContent(summaries);
                emailService.sendHtmlMail(assessor.getEmail(), subject, content);
            }
        });
    }

    /**
     * 获取可分配审核的任务列表
     * @param status 任务状态（可选）
     * @return 任务列表
     */
    public List<TaskVO> getAssignableTasks(String status) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();

        // 只查询已提交或审核中的任务，这些任务可以继续分配审核项目
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Task::getStatus, status);
        } else {
            wrapper.in(Task::getStatus, List.of("SUBMITTED", "REVIEWING"));
        }

        wrapper.orderByDesc(Task::getCreateTime);

        List<Task> tasks = taskMapper.selectList(wrapper);
        List<TaskVO> result = new ArrayList<>();

        for (Task task : tasks) {
            TaskVO vo = convertToTaskVO(task);
            if (vo.getAvailableReviewProjects() != null && !vo.getAvailableReviewProjects().isEmpty()) {
                result.add(vo);
            }
        }

        return result;
    }


    /**
     * 过滤特殊班级（茅班、利兹）
     * @param teachers 教师列表
     * @param major 当前查询的专业名称
     * @return 过滤后的教师列表
     */
    private List<Teacher> filterSpecialClasses(List<Teacher> teachers, String major) {
        if (major == null || teachers == null) {
            return teachers;
        }
        // 如果专业名称本身包含"茅班"或"利兹"，则不进行过滤（保留所有班级）
        if (major.contains("茅班") || major.contains("利兹")) {
            return teachers;
        }
        // 否则过滤掉优选班中包含"（茅班）"或"（利兹）"的教师
        return teachers.stream()
                .filter(t -> t.getPreferred() == null ||
                        (!t.getPreferred().contains("（茅班）") && !t.getPreferred().contains("（利兹）")))
                .collect(Collectors.toList());
    }

    /**
     * 根据教师姓名或工号获取或创建教师用户
     */
    private Long getOrCreateTeacherUser(String teacherInput) {
        User teacher = getOrCreateTeacherUserEntity(teacherInput);
        return teacher != null ? teacher.getId() : null;
    }

    private User getOrCreateTeacherUserEntity(String teacherInput) {
        if (teacherInput == null || teacherInput.trim().isEmpty()) {
            return null;
        }
        teacherInput = teacherInput.trim();

        // 按工号查找
        LambdaQueryWrapper<User> wrapperByWorkId = new LambdaQueryWrapper<>();
        wrapperByWorkId.eq(User::getWorkId, teacherInput);
        User user = userMapper.selectOne(wrapperByWorkId);
        if (user != null && "TEACHER".equals(user.getRole())) {
            return user;
        }

        // 按姓名查找
        LambdaQueryWrapper<User> wrapperByName = new LambdaQueryWrapper<>();
        wrapperByName.eq(User::getRealName, teacherInput)
                .eq(User::getRole, "TEACHER");
        user = userMapper.selectOne(wrapperByName);
        if (user != null) {
            return user;
        }

        // 不存在，创建新教师
        String workId;
        if (teacherInput.matches("\\d+")) {
            workId = teacherInput;
            LambdaQueryWrapper<User> checkWorkId = new LambdaQueryWrapper<>();
            checkWorkId.eq(User::getWorkId, workId);
            if (userMapper.selectCount(checkWorkId) > 0) {
                workId = generateUniqueWorkId(teacherInput);
            }
        } else {
            workId = generateUniqueWorkId(teacherInput);
        }

        String username = "teacher_" + workId;
        LambdaQueryWrapper<User> checkUsername = new LambdaQueryWrapper<>();
        checkUsername.eq(User::getUsername, username);
        if (userMapper.selectCount(checkUsername) > 0) {
            username = username + "_" + System.currentTimeMillis();
        }

        User newTeacher = new User();
        newTeacher.setUsername(username);
        newTeacher.setWorkId(workId);
        newTeacher.setRealName(teacherInput);
        newTeacher.setEmail(generateEmail(teacherInput));
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
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (teachers.size() > 4) {
            return teachers.subList(0, 4);
        }
        return teachers;
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
        if (teachers.size() > 4) {
            return teachers.subList(0, 4);
        }
        return teachers;
    }

    private List<User> getTaskTeachers(Task task) {
        return TaskTeacherUtils.getTeacherIds(task).stream()
                .map(userMapper::selectById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String joinTeacherNames(List<User> teachers) {
        return teachers.isEmpty()
                ? "未分配教师"
                : teachers.stream().map(User::getRealName).filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    private String joinTeacherWorkIds(List<User> teachers) {
        return teachers.stream()
                .map(User::getWorkId)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
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
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return teacherNames.stream().anyMatch(taskTeacherNames::contains);
    }

    private TaskVO convertToTaskVO(Task task) {
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

        vo.setStatusDesc(getTaskStatusDescription(task.getStatus()));
        vo.setAvailableReviewProjects(reviewWorkflowService.getAvailableReviewProjects(task));
        vo.setDescription(normalizeTaskDescription(task.getDescription()));
        vo.setReviewProjectsDescription(getReviewProjectsDescription(task));

        // 获取课程信息
        Teacher teacherEntity = null;
        if (task.getTeacherId() != null) {
            teacherEntity = teacherMapper.selectById(task.getCourseId());
            // 校验：如果查到的教学班与任务的教师姓名不一致，则视为无效（ID冲突）
            if (teacherEntity != null && !matchesTeacherRecord(task, teacherEntity)) {
                teacherEntity = null; // 教师姓名不匹配，回退到 course 表
            }
        }

        if (teacherEntity != null) {
            // 教学班信息
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
            // 未来课程（从 course 表获取）
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

        // 教师信息（支持多教师）
        List<User> taskTeachers = getTaskTeachers(task);
        if (!taskTeachers.isEmpty()) {
            vo.setTeacherWorkId(joinTeacherWorkIds(taskTeachers));
            vo.setTeacherName(joinTeacherNames(taskTeachers));
        }

        // 审核员信息
        User assessor = userMapper.selectById(task.getAssessorId());
        if (assessor != null) {
            vo.setAssessorWorkId(assessor.getWorkId());
            vo.setAssessorName(assessor.getRealName());
        }

        return vo;
    }

    /**
     * 将培养方案中的学期（如"短1学期"）转换为教务网实际学期的标准格式（如"2024-2025-2"）
     * @param semester 原始学期字符串，如"短1学期"或"2024-2025-2"
     * @param grade 年级，如"2023"
     * @return 转换后的标准学期格式
     */
    private String convertShortSemesterToActual(String semester, String grade) {
        if (semester == null || grade == null) return semester;
        if (semester.startsWith("短")) {
            Pattern p = Pattern.compile("短(\\d+)学期");
            Matcher m = p.matcher(semester);
            if (m.find()) {
                int shortNum = Integer.parseInt(m.group(1));
                // 短1→第2学期，短2→第4学期，短3→第6学期，短4→第8学期
                int actualSemesterNum = shortNum * 2;
                int baseYear = Integer.parseInt(grade);
                int yearOffset = (actualSemesterNum - 1) / 2;
                int startYear = baseYear + yearOffset;
                int endYear = startYear + 1;
                int flag = (actualSemesterNum % 2 == 1) ? 1 : 2;
                return startYear + "-" + endYear + "-" + flag;
            }
        }
        return semester;
    }

    /**
     * 去除课程名称中的罗马数字（如"高等数学I" -> "高等数学"）
     * 匹配末尾的罗马数字（I, II, III, IV, V, VI, VII, VIII, IX, X 等）
     */
    private String normalizeCourseName(String courseName) {
        if (courseName == null) return null;
        // 匹配末尾的罗马数字（可能包含空格）
        String pattern = "\\s*[IVX]+$";
        return courseName.replaceAll(pattern, "").trim();
    }

    /**
     * 获取任务状态描述
     * @param status 状态码
     * @return 状态描述
     */
    private String getStatusDescription(String status) {
        if (status == null) return "";
        switch (status) {
            case "PENDING_UPLOAD":
                return "待上传";
            case "SUBMITTED":
                return "待审核";
            case "REVIEWING":
                return "审核中";
            case "APPROVED":
                return "审核通过";
            case "NEED_REVISION":
                return "需修改";
            default:
                return status;
        }
    }

    private String getTaskStatusDescription(String status) {
        return getStatusDescription(status);
    }

    /**
     * 获取已备案档案列表
     * @param semester 学期（可选）
     * @param major 专业（可选）
     * @param courseName 课程名称（可选）
     * @param teacherName 教师姓名（可选）
     * @param status 任务状态（可选，支持全部状态）
     * @return 已备案档案列表
     */
    public List<ArchiveTaskVO> getArchiveList(String semester, String major, String courseName,
                                             String teacherName, String status) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();

        // 将导出格式的学期转换为标准格式用于筛选
        String standardSemester = null;
        if (semester != null && !semester.isEmpty()) {
            standardSemester = SemesterUtils.fromExportFormat(semester);
        }

        // 关联查询教师表获取课程信息
        // 先查询符合条件的任务ID
        List<Task> tasks = taskMapper.selectList(wrapper);

        List<ArchiveTaskVO> result = new ArrayList<>();

        for (Task task : tasks) {
            // 获取教学班信息
            Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
            if (teacherEntity == null) {
                // 如果教学班不存在，尝试从course表获取
                Course course = courseMapper.selectById(task.getCourseId());
                if (course == null) continue;

                // 获取教师信息
                List<User> taskTeachers = getTaskTeachers(task);
                if (taskTeachers.isEmpty()) continue;
                String taskTeacherNames = joinTeacherNames(taskTeachers);
                String taskTeacherWorkIds = joinTeacherWorkIds(taskTeachers);

                // 检查筛选条件
                if (standardSemester != null && !standardSemester.equals(course.getSemester())) continue;
                if (major != null && !major.isEmpty() && !major.equals(course.getMajor())) continue;
                if (courseName != null && !courseName.isEmpty() && !course.getCourseName().contains(courseName)) continue;
                if (teacherName != null && !teacherName.isEmpty() && !taskTeacherNames.contains(teacherName)) continue;
                if (status != null && !status.isEmpty() && !status.equals(task.getStatus())) continue;

                ArchiveTaskVO vo = new ArchiveTaskVO();
                vo.setId(task.getId());
                vo.setCourseId(task.getCourseId());
                vo.setCourseName(course.getCourseName());
                vo.setCourseCode("");
                vo.setTeacherId(task.getTeacherId());
                vo.setTeacherWorkId(taskTeacherWorkIds);
                vo.setTeacherName(taskTeacherNames);
                vo.setAssessorId(task.getAssessorId());
                if (task.getAssessorId() != null) {
                    User assessor = userMapper.selectById(task.getAssessorId());
                    if (assessor != null) {
                        vo.setAssessorName(assessor.getRealName());
                    }
                }
                vo.setDeadline(task.getDeadline());
                vo.setStatus(task.getStatus());
                vo.setStatusDesc(getStatusDescription(task.getStatus()));
                vo.setCreateTime(task.getCreateTime());
                vo.setUpdateTime(task.getUpdateTime());
                vo.setDescription(normalizeTaskDescription(task.getDescription()));
                vo.setMaterialRequirements(task.getMaterialRequirements());
                vo.setReviewProjectsDescription(getReviewProjectsDescription(task));
                vo.setGrade(course.getGrade());
                vo.setSemester(course.getSemester());
                vo.setMajor(course.getMajor());

                // 获取文件数量
                LambdaQueryWrapper<com.swjtu.certification.entity.File> fileWrapper = new LambdaQueryWrapper<>();
                fileWrapper.eq(com.swjtu.certification.entity.File::getTaskId, task.getId());
                fileWrapper.eq(com.swjtu.certification.entity.File::getStatus, "UPLOADED");
                Long fileCount = fileMapper.selectCount(fileWrapper);
                vo.setFileCount(fileCount.intValue());

                // 获取提交时间
                if ("SUBMITTED".equals(task.getStatus()) || "REVIEWING".equals(task.getStatus()) ||
                        "APPROVED".equals(task.getStatus()) || "NEED_REVISION".equals(task.getStatus())) {
                    vo.setSubmitTime(task.getUpdateTime());
                }

                result.add(vo);
            } else {
                // 从教学班获取信息
                List<User> taskTeachers = getTaskTeachers(task);
                if (taskTeachers.isEmpty()) continue;
                String taskTeacherNames = joinTeacherNames(taskTeachers);
                String taskTeacherWorkIds = joinTeacherWorkIds(taskTeachers);

                // 检查筛选条件
                if (standardSemester != null && !standardSemester.equals(teacherEntity.getSemester())) continue;
                if (major != null && !major.isEmpty() && !major.equals(teacherEntity.getDepartment())) continue;
                if (courseName != null && !courseName.isEmpty() && !teacherEntity.getCourseName().contains(courseName)) continue;
                if (teacherName != null && !teacherName.isEmpty() && !taskTeacherNames.contains(teacherName)) continue;
                if (status != null && !status.isEmpty() && !status.equals(task.getStatus())) continue;

                ArchiveTaskVO vo = new ArchiveTaskVO();
                vo.setId(task.getId());
                vo.setCourseId(task.getCourseId());
                vo.setCourseName(teacherEntity.getCourseName());
                vo.setCourseCode(teacherEntity.getCourseCode());
                vo.setTeachingClass(teacherEntity.getTeachingClass());
                vo.setTeacherId(task.getTeacherId());
                vo.setTeacherWorkId(taskTeacherWorkIds);
                vo.setTeacherName(taskTeacherNames);
                vo.setAssessorId(task.getAssessorId());
                if (task.getAssessorId() != null) {
                    User assessor = userMapper.selectById(task.getAssessorId());
                    if (assessor != null) {
                        vo.setAssessorName(assessor.getRealName());
                    }
                }
                vo.setDeadline(task.getDeadline());
                vo.setStatus(task.getStatus());
                vo.setStatusDesc(getStatusDescription(task.getStatus()));
                vo.setCreateTime(task.getCreateTime());
                vo.setUpdateTime(task.getUpdateTime());
                vo.setDescription(normalizeTaskDescription(task.getDescription()));
                vo.setMaterialRequirements(task.getMaterialRequirements());
                vo.setReviewProjectsDescription(getReviewProjectsDescription(task));

                // 从教学班提取年级
                if (teacherEntity.getPreferred() != null) {
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{4}");
                    java.util.regex.Matcher matcher = pattern.matcher(teacherEntity.getPreferred());
                    if (matcher.find()) {
                        vo.setGrade(matcher.group());
                    }
                }
                vo.setSemester(teacherEntity.getSemester());
                vo.setMajor(teacherEntity.getDepartment());

                // 获取文件数量
                LambdaQueryWrapper<com.swjtu.certification.entity.File> fileWrapper = new LambdaQueryWrapper<>();
                fileWrapper.eq(com.swjtu.certification.entity.File::getTaskId, task.getId());
                fileWrapper.eq(com.swjtu.certification.entity.File::getStatus, "UPLOADED");
                Long fileCount = fileMapper.selectCount(fileWrapper);
                vo.setFileCount(fileCount.intValue());

                // 获取提交时间和审核时间
                if ("SUBMITTED".equals(task.getStatus()) || "REVIEWING".equals(task.getStatus()) ||
                        "APPROVED".equals(task.getStatus()) || "NEED_REVISION".equals(task.getStatus())) {
                    vo.setSubmitTime(task.getUpdateTime());
                }

                if ("APPROVED".equals(task.getStatus()) || "NEED_REVISION".equals(task.getStatus())) {
                    vo.setReviewTime(task.getUpdateTime());
                }

                result.add(vo);
            }
        }

        // 按创建时间倒序排列
        result.sort((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()));

        return result;
    }

    /**
     * 批量导出已备案档案（ZIP压缩包）
     * @param taskIds 任务ID列表
     * @return ZIP文件名
     * @throws IOException
     */
    public String exportArchive(List<Long> taskIds) throws IOException {
        // 确保下载目录存在
        Path downloadDir = Paths.get(downloadPath);
        if (!Files.exists(downloadDir)) {
            Files.createDirectories(downloadDir);
        }

        // 获取第一个任务信息来生成ZIP文件名
        Task firstTask = null;
        for (Long taskId : taskIds) {
            firstTask = taskMapper.selectById(taskId);
            if (firstTask != null) break;
        }

        // 生成ZIP文件名：学期名+课程名+教师姓名.zip（从Teacher表获取学期和课程名）
        String zipFileName;
        if (firstTask != null) {
            Teacher firstTeacherEntity = teacherMapper.selectById(firstTask.getCourseId());
            List<User> firstTaskTeachers = getTaskTeachers(firstTask);

            String semester = firstTeacherEntity != null ? firstTeacherEntity.getSemester() : "";
            String courseName = firstTeacherEntity != null ? firstTeacherEntity.getCourseName() : "";
            String teacherName = !firstTaskTeachers.isEmpty()
                    ? joinTeacherNames(firstTaskTeachers)
                    : (firstTeacherEntity != null ? firstTeacherEntity.getTeacherName() : "");

            // 将学期转换为导出格式（如 2025-2026-2 → 2025-2026学年第二学期）
            String exportSemester = SemesterUtils.toExportFormat(semester);

            zipFileName = exportSemester + "_" + courseName + "_" + teacherName + ".zip";
            zipFileName = zipFileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        } else {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            zipFileName = "档案导出_" + timestamp + ".zip";
        }
        Path zipFilePath = downloadDir.resolve(zipFileName);

        // 用于存储已处理的文件夹，避免重复
        Set<String> processedFolders = new HashSet<>();
        Set<String> processedSubFolders = new HashSet<>();

        int taskCount = 0;
        int fileCount = 0;
        int reviewCount = 0;

        try (java.util.zip.ZipOutputStream zipOut = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {

            for (Long taskId : taskIds) {
                taskCount++;

                // 获取任务信息
                Task task = taskMapper.selectById(taskId);
                if (task == null) {
                    continue;
                }

                // 从Teacher表获取学期和课程名
                Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
                String semester = "";
                String courseName = "";
                String teacherName = "";

                if (teacherEntity != null) {
                    semester = teacherEntity.getSemester();
                    courseName = teacherEntity.getCourseName();
                    teacherName = teacherEntity.getTeacherName();
                }

                List<User> taskTeachers = getTaskTeachers(task);
                if (!taskTeachers.isEmpty()) {
                    teacherName = joinTeacherNames(taskTeachers);
                }

                // 将学期转换为导出格式（如 2025-2026-2 → 2025-2026学年第二学期）
                String exportSemester = SemesterUtils.toExportFormat(semester);

                // 文件夹名称：学期名+课程名+教师姓名
                String folderName = exportSemester + "_" + courseName + "_" + teacherName;
                folderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_"); // 替换非法字符

                // 添加文件夹（如果尚未添加）
                if (!processedFolders.contains(folderName)) {
                    java.util.zip.ZipEntry folderEntry = new java.util.zip.ZipEntry(folderName + "/");
                    zipOut.putNextEntry(folderEntry);
                    zipOut.closeEntry();
                    processedFolders.add(folderName);
                }

                List<TaskItemUtils.ItemConfig> requiredItems = TaskItemUtils.parseRequiredItems(task.getMaterialRequirements());
                for (TaskItemUtils.ItemConfig item : requiredItems) {
                    String subFolderName = folderName + "/" + item.getFolderName() + "/";
                    if (processedSubFolders.add(subFolderName)) {
                        java.util.zip.ZipEntry subFolderEntry = new java.util.zip.ZipEntry(subFolderName);
                        zipOut.putNextEntry(subFolderEntry);
                        zipOut.closeEntry();
                    }
                }

                // 获取任务文件
                LambdaQueryWrapper<com.swjtu.certification.entity.File> fileWrapper = new LambdaQueryWrapper<>();
                fileWrapper.eq(com.swjtu.certification.entity.File::getTaskId, taskId);
                fileWrapper.eq(com.swjtu.certification.entity.File::getStatus, "UPLOADED");
                List<com.swjtu.certification.entity.File> files = fileMapper.selectList(fileWrapper);

                // 添加文件到ZIP
                for (com.swjtu.certification.entity.File file : files) {
                    java.io.File sourceFile = new java.io.File(file.getFilePath());
                    if (sourceFile.exists()) {
                        String itemCode = TaskItemUtils.resolveItemCodeFromPath(file.getFilePath());
                        String itemFolderName = itemCode == null
                                ? TaskItemUtils.getDefaultFolderName()
                                : TaskItemUtils.getItemFolderName(itemCode);
                        String subFolderName = folderName + "/" + itemFolderName + "/";
                        if (processedSubFolders.add(subFolderName)) {
                            java.util.zip.ZipEntry subFolderEntry = new java.util.zip.ZipEntry(subFolderName);
                            zipOut.putNextEntry(subFolderEntry);
                            zipOut.closeEntry();
                        }

                        java.util.zip.ZipEntry fileEntry = new java.util.zip.ZipEntry(
                                subFolderName + TaskItemUtils.sanitizeFolderName(file.getOriginalName()));
                        zipOut.putNextEntry(fileEntry);

                        try (FileInputStream fis = new FileInputStream(sourceFile)) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = fis.read(buffer)) > 0) {
                                zipOut.write(buffer, 0, len);
                            }
                        }
                        zipOut.closeEntry();
                        fileCount++;
                    }
                }
            }

            // 收集所有任务的审核记录，按文件夹分组
            Map<String, List<Map<String, Object>>> folderReviews = new HashMap<>();

            for (Long taskId : taskIds) {
                Task task = taskMapper.selectById(taskId);
                if (task == null) continue;

                Teacher teacherEntity = teacherMapper.selectById(task.getCourseId());
                String semester = "";
                String courseName = "";
                String teacherName = "";

                if (teacherEntity != null) {
                    semester = teacherEntity.getSemester();
                    courseName = teacherEntity.getCourseName();
                    teacherName = teacherEntity.getTeacherName();
                }

                List<User> taskTeachers = getTaskTeachers(task);
                if (!taskTeachers.isEmpty()) {
                    teacherName = joinTeacherNames(taskTeachers);
                }

                // 将学期转换为导出格式（如 2025-2026-2 → 2025-2026学年第二学期）
                String exportSemester = SemesterUtils.toExportFormat(semester);

                String folderName = exportSemester + "_" + courseName + "_" + teacherName;
                folderName = folderName.replaceAll("[\\\\/:*?\"<>|]", "_");

                // 获取审核记录
                LambdaQueryWrapper<com.swjtu.certification.entity.ReviewRecord> reviewWrapper = new LambdaQueryWrapper<>();
                reviewWrapper.eq(com.swjtu.certification.entity.ReviewRecord::getTaskId, taskId);
                reviewWrapper.orderByAsc(com.swjtu.certification.entity.ReviewRecord::getReviewTime);
                List<com.swjtu.certification.entity.ReviewRecord> reviewRecords = reviewRecordMapper.selectList(reviewWrapper);

                for (com.swjtu.certification.entity.ReviewRecord record : reviewRecords) {
                    User reviewer = userMapper.selectById(record.getAssessorId());
                    String reviewerName = reviewer != null ? reviewer.getRealName() : "未知";

                    Map<String, Object> reviewData = new HashMap<>();
                    reviewData.put("courseName", courseName);
                    reviewData.put("teacherName", teacherName);
                    reviewData.put("semester", exportSemester); // 使用导出格式的学期
                    reviewData.put("taskDescription", normalizeTaskDescription(task.getDescription()));
                    reviewData.put("uploadRequirements", TaskItemUtils.formatForDisplay(task.getMaterialRequirements()));
                    reviewData.put("reviewProjects", getReviewProjectsDescription(task));
                    reviewData.put("reviewItem", record.getItemName() != null ? record.getItemName() : "");
                    reviewData.put("reviewTime", record.getReviewTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    reviewData.put("reviewerName", reviewerName);
                    reviewData.put("reviewStatus", record.getReviewStatus());
                    reviewData.put("score", record.getScore() != null ? record.getScore().toString() : "");
                    reviewData.put("comment", record.getComment() != null ? record.getComment() : "");
                    reviewData.put("suggestions", record.getSuggestions() != null ? record.getSuggestions() : "");

                    folderReviews.computeIfAbsent(folderName, k -> new ArrayList<>()).add(reviewData);
                    reviewCount++;
                }
            }

            System.out.println("总共收集了 " + reviewCount + " 条审核记录");
            System.out.println("涉及 " + folderReviews.size() + " 个文件夹");

            // 为每个文件夹创建审核意见CSV文件
            for (Map.Entry<String, List<Map<String, Object>>> entry : folderReviews.entrySet()) {
                String folderName = entry.getKey();
                List<Map<String, Object>> reviews = entry.getValue();

                if (reviews.isEmpty()) continue;

                // 创建CSV内容
                StringBuilder csvContent = new StringBuilder();
                // CSV表头
                csvContent.append("课程名称,教师姓名,学期,任务说明,需要上传的文件,审核项目,本次审核目录,审核时间,审核员,审核状态,评分,审核意见,修改建议\n");

                // CSV数据行
                for (Map<String, Object> review : reviews) {
                    csvContent.append(escapeCsv((String) review.get("courseName"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("teacherName"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("semester"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("taskDescription"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("uploadRequirements"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("reviewProjects"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("reviewItem"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("reviewTime"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("reviewerName"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("reviewStatus"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("score"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("comment"))).append(",");
                    csvContent.append(escapeCsv((String) review.get("suggestions"))).append("\n");
                }

                // 创建CSV文件条目
                java.util.zip.ZipEntry csvEntry = new java.util.zip.ZipEntry(folderName + "/审核意见.csv");
                zipOut.putNextEntry(csvEntry);
                zipOut.write(csvContent.toString().getBytes("GBK"));
                zipOut.closeEntry();
            }
        }

        return zipFileName;
    }

    /**
     * CSV字段转义
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // 如果包含逗号、双引号或换行符，需要用双引号包裹，并转义双引号
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 获取下载路径
     */
    public String getDownloadPath() {
        return downloadPath;
    }

    /**
     * 生成教师通知邮件内容
     */
    private String buildTeacherEmailContent(String courseName, String teachingClass, String deadline) {
        return String.format(
                "<h3>📧 专业认证年度备案 - 新任务通知</h3>" +
                        "<p>您收到了一条新的备案任务，请及时登录系统上传相关材料。</p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>课程名称：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>教学班：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>截止日期：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://your-system-domain/teacher/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>进入系统查看</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, teachingClass, deadline
        );
    }

    /**
     * 生成审核员通知邮件内容
     */
    private String buildAssessorEmailContent(String courseName, String teachingClass, String teacherName, String deadline) {
        return String.format(
                "<h3>📧 专业认证年度备案 - 新审核任务通知</h3>" +
                        "<p>您收到了一条新的审核任务，请及时登录系统进行审核。</p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>课程名称：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>教学班：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>任课教师：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>截止日期：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://your-system-domain/assessor/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>进入系统审核</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, teachingClass, teacherName, deadline
        );
    }

    private String buildRemindEmailContent(String courseName, String teachingClass, String statusDesc, String deadline) {
        return String.format(
                "<h3>⏰ 专业认证年度备案 - 催办提醒</h3>" +
                        "<p>您有一个备案任务即将截止或已逾期，请尽快处理。</p>" +
                        "<table border='0' cellpadding='8' style='border-collapse: collapse;'>" +
                        "<tr><td><strong>课程名称：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>教学班：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>当前状态：</strong></td><td>%s</td></tr>" +
                        "<tr><td><strong>截止日期：</strong></td><td>%s</td></tr>" +
                        "</table>" +
                        "<p><a href='http://localhost:8080' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>进入系统处理</a></p>" +
                        "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>",
                courseName, teachingClass, statusDesc, deadline
        );
    }

    private String normalizeTaskDescription(String rawDescription) {
        if (rawDescription == null || rawDescription.trim().isEmpty()) {
            return "";
        }
        List<String> segments = Arrays.stream(rawDescription.split("[；;\\n]+"))
                .map(String::trim)
                .filter(segment -> !segment.isEmpty())
                .filter(segment -> !segment.startsWith("需要上传的文件："))
                .filter(segment -> !segment.startsWith("需要上传的文件:"))
                .filter(segment -> !segment.startsWith("审核项目："))
                .filter(segment -> !segment.startsWith("审核项目:"))
                .collect(Collectors.toList());
        return segments.isEmpty() ? rawDescription.trim() : String.join("；", segments);
    }

    private String getReviewProjectsDescription(Task task) {
        List<ReviewProjectVO> projects = reviewWorkflowService.getAssignedProjects(task);
        if (projects.isEmpty()) {
            return "";
        }
        return projects.stream()
                .map(project -> project.getCode() + "-" + project.getName())
                .collect(Collectors.joining("、"));
    }

    private String buildReviewAssignmentEmailContent(List<String> taskSummaries) {
        String itemsHtml = taskSummaries.stream()
                .map(summary -> "<li>" + summary + "</li>")
                .collect(Collectors.joining());
        return "<h3>📋 专业认证年度备案 - 审核任务分配通知</h3>" +
                "<p>您有新的审核任务已分配，请及时登录系统处理。</p>" +
                "<ul>" + itemsHtml + "</ul>" +
                "<p><a href='http://localhost:8080/assessor/tasks' style='display: inline-block; padding: 10px 20px; background-color: #165DFF; color: #fff; text-decoration: none; border-radius: 5px;'>进入系统审核</a></p>" +
                "<p style='color: #999; font-size: 12px;'>本邮件由系统自动发送，请勿回复。</p>";
    }
}
