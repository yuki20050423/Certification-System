package com.swjtu.certification.controller;

import com.swjtu.certification.dto.AddMemberDTO;
import com.swjtu.certification.dto.BatchTaskDTO;
import com.swjtu.certification.dto.GenerateTaskDTO;
import com.swjtu.certification.dto.ReviewAssignmentDTO;
import com.swjtu.certification.vo.CourseTeachingVO;
import com.swjtu.certification.entity.User;
import com.swjtu.certification.service.AdminService;
import com.swjtu.certification.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletOutputStream;
import javax.validation.Valid;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> getDashboard() {
        try {
            Map<String, Object> dashboard = adminService.getDashboard();
            return Result.success("查询成功", dashboard);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/tasks")
    public Result<List<TaskVO>> getAllTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        try {
            List<TaskVO> tasks = adminService.getAllTasks(status, page, pageSize);
            return Result.success("查询成功", tasks);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/courses")
    public Result<PageResult<CourseVO>> getAllCourses(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        try {
            if (pageSize == null) pageSize = 10;
            if (page == null) page = 1;
            PageResult<CourseVO> pageResult = adminService.getAllCourses(grade, major, semester, status, search, page, pageSize);
            return Result.success("查询成功", pageResult);
        } catch (Exception e) {
            e.printStackTrace();
            String message = e.getMessage();
            if (message == null || message.isEmpty()) {
                message = "查询失败，请稍后重试";
            }
            return Result.error(message);
        }
    }

    @GetMapping("/grades")
    public Result<List<String>> getAllGrades() {
        try {
            List<String> grades = adminService.getAllGrades();
            return Result.success("查询成功", grades);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/semesters")
    public Result<List<String>> getAllSemesters(@RequestParam(required = false) String grade) {
        try {
            List<String> semesters = adminService.getAllSemesters(grade);
            return Result.success("查询成功", semesters);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/majors")
    public Result<List<String>> getAllMajors() {
        try {
            List<String> majors = adminService.getAllMajors();
            return Result.success("查询成功", majors);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/teachers")
    public Result<List<UserVO>> getAllTeachers(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name) {
        try {
            List<UserVO> teachers = adminService.getAllTeachers(code, name);
            return Result.success("查询成功", teachers);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/assessors")
    public Result<List<UserVO>> getAllAssessors(
            @RequestParam(required = false) Long excludeTeacherId) {
        try {
            List<UserVO> assessors = adminService.getAllAssessors(excludeTeacherId);
            return Result.success("查询成功", assessors);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/batch")
    public Result<Object> batchCreateTasks(@Valid @RequestBody BatchTaskDTO batchTaskDTO) {
        try {
            adminService.batchCreateTasks(batchTaskDTO);
            return Result.success("批量创建任务成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/tasks/{id}/deadline")
    public Result<Object> updateTaskDeadline(
            @PathVariable Long id,
            @RequestParam String deadline) {
        try {
            adminService.updateTaskDeadline(id, deadline);
            return Result.success("更新截止日期成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/tasks/{id}/description")
    public Result<Object> updateTaskDescription(
            @PathVariable Long id,
            @RequestParam String description) {
        try {
            adminService.updateTaskDescription(id, description);
            return Result.success("更新任务说明成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/tasks/{id}/material-requirements")
    public Result<Object> updateTaskMaterialRequirements(
            @PathVariable Long id,
            @RequestParam String materialRequirements) {
        try {
            adminService.updateTaskMaterialRequirements(id, materialRequirements);
            return Result.success("更新材料要求成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/tasks/{id}/remind")
    public Result<Object> remindTask(@PathVariable Long id) {
        try {
            adminService.remindTask(id);
            return Result.success("催办通知发送成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> statistics = adminService.getStatistics();
            return Result.success("查询成功", statistics);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/uploaded-course-files")
    public Result<List<String>> getUploadedCourseFiles() {
        try {
            List<String> files = adminService.getUploadedCourseFiles();
            return Result.success("查询成功", files);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/tasks/{id}")
    public Result<Object> deleteTask(@PathVariable Long id) {
        try {
            adminService.deleteTask(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/members")
    public Result<List<UserVO>> getAllMembers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name) {
        try {
            List<UserVO> members = adminService.getAllMembers(role, code, name);
            return Result.success("查询成功", members);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/members")
    public Result<Object> addMember(@Valid @RequestBody AddMemberDTO addMemberDTO) {
        try {
            adminService.addMember(addMemberDTO);
            return Result.success("添加成员成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/members/{id}/status")
    public Result<Object> updateMemberStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        try {
            adminService.updateMemberStatus(id, status);
            return Result.success("更新状态成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/members/{id}/role")
    public Result<Object> updateMemberRole(
            @PathVariable Long id,
            @RequestParam String role) {
        try {
            adminService.updateMemberRole(id, role);
            return Result.success("更新角色成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/members/{id}")
    public Result<Object> deleteMember(@PathVariable Long id) {
        try {
            adminService.deleteMember(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/upload/course-file")
    public Result<Object> uploadCourseFile(@RequestParam("file") MultipartFile file,
                                       @RequestParam(value = "charset", defaultValue = "UTF-8") String charset) {
        try {
            adminService.uploadAndParseWordFile(file, charset);
            return Result.success("文件上传并解析成功");
        } catch (Exception e) {
            return Result.error("文件处理失败: " + e.getMessage());
        }
    }

    @GetMapping("/fetch-teachers")
    public Result<List<com.swjtu.certification.entity.Teacher>> fetchTeachers(@RequestParam String courseName, @RequestParam String semester, @RequestParam(required = false) String grade, @RequestParam(required = false) String major) {
        try {
            List<com.swjtu.certification.entity.Teacher> teachers = adminService.fetchTeachersFromWeb(courseName, semester, grade, major);
            return Result.success("获取教师列表成功", teachers);
        } catch (Exception e) {
            return Result.error("获取教师列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/fetch-teachers-current")
    public Result<List<com.swjtu.certification.entity.Teacher>> fetchTeachersCurrent(@RequestParam String courseName) {
        try {
            List<com.swjtu.certification.entity.Teacher> teachers = adminService.fetchTeachersFromWebCurrent(courseName);
            return Result.success("获取当前学期教师列表成功", teachers);
        } catch (Exception e) {
            return Result.error("获取教师列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/courses/{courseId}/teachers")
    public Result<List<UserVO>> getTeachersByCourse(@PathVariable Long courseId) {
        try {
            List<UserVO> teachers = adminService.getTeachersByCourse(courseId);
            return Result.success("获取课程教师列表成功", teachers);
        } catch (Exception e) {
            return Result.error("获取课程教师列表失败: " + e.getMessage());
        }
    }

    @PostMapping("/teachers/auto-add")
    public Result<Object> autoAddTeachers(@RequestBody List<User> teachers) {
        try {
            adminService.autoAddTeachers(teachers);
            return Result.success("自动添加教师成功");
        } catch (Exception e) {
            return Result.error("自动添加教师失败: " + e.getMessage());
        }
    }

    @PostMapping("/teachers/save-to-user")
    public Result<List<UserVO>> saveTeachersToUserTable(@RequestBody List<Long> teacherIds) {
        try {
            List<UserVO> teachers = adminService.saveTeachersToUserTable(teacherIds);
            return Result.success("保存教师成功", teachers);
        } catch (Exception e) {
            return Result.error("保存教师失败: " + e.getMessage());
        }
    }

    @PostMapping("/manual-add-task")
    public Result<UserVO> manualAddTask(@RequestBody Map<String, String> request) {
        try {
            String courseName = request.get("courseName");
            String teachingClass = request.get("teachingClass");
            String teacherName = request.get("teacherName");
            String workId = request.get("workId");

            UserVO user = adminService.manualAddTask(courseName, teachingClass, teacherName, workId);
            return Result.success("手动添加任务成功", user);
        } catch (Exception e) {
            return Result.error("手动添加任务失败: " + e.getMessage());
        }
    }

    @GetMapping("/check-teacher-by-name")
    public Result<Map<String, Object>> checkTeacherByName(@RequestParam String teacherName) {
        try {
            Map<String, Object> result = adminService.checkTeacherByName(teacherName);
            return Result.success("查询成功", result);
        } catch (Exception e) {
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/departments")
    public Result<List<String>> getAllDepartments() {
        try {
            List<String> departments = adminService.getAllDepartmentsFromCourse();
            return Result.success("查询成功", departments);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/courses-by-grade")
    public Result<List<CourseItemVO>> getCoursesByGrade(
            @RequestParam String major,
            @RequestParam String grade) {
        try {
            List<CourseItemVO> items = adminService.getCoursesByGrade(major, grade);
            return Result.success("查询成功", items);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/courses-by-semester")
    public Result<List<CourseItemVO>> getCoursesBySemester(
            @RequestParam String major,
            @RequestParam String semester) {
        try {
            List<CourseItemVO> courses = adminService.getCoursesBySemester(major, semester);
            return Result.success("查询成功", courses);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/generate-tasks")
    public Result<Map<String, Object>> generateTasks(@RequestBody GenerateTaskDTO generateTaskDTO) {
        try {
            Map<String, Object> downloadUrls = adminService.generateTasks(generateTaskDTO);
            return Result.success("任务生成成功", downloadUrls);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @Value("${file.download.path:./downloads}")
    private String downloadPath;

    /**
     * 下载生成的教师清单 Excel 文件
     */
    @GetMapping("/download/excel/{filename}")
    public void downloadExcelFile(@PathVariable String filename, HttpServletResponse response) {
        try {
            Path filePath = (Path) Paths.get(downloadPath).resolve(filename);
            java.io.File file = ((java.nio.file.Path) filePath).toFile();
            if (!file.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在");
                return;
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            response.setContentLengthLong(file.length());

            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, len);
                }
                os.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("下载失败：" + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @GetMapping("/course-plans")
    public Result<List<String>> getCoursePlanGrades(@RequestParam String major) {
        try {
            List<String> grades = adminService.getCoursePlanGrades(major);
            return Result.success("查询成功", grades);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/copy-course-plan")
    public Result<Object> copyCoursePlan(@RequestParam String major,
                                         @RequestParam String sourceGrade,
                                         @RequestParam String targetGrade) {
        try {
            adminService.copyCoursePlan(major, sourceGrade, targetGrade);
            return Result.success("复制成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 审核任务分配
     */
    @PostMapping("/review-assignments")
    public Result<Object> assignReviewTasks(@Valid @RequestBody ReviewAssignmentDTO reviewAssignmentDTO) {
        try {
            adminService.assignReviewTasks(reviewAssignmentDTO);
            return Result.success("审核任务分配成功");
        } catch (Exception e) {
            return Result.error("审核任务分配失败：" + e.getMessage());
        }
    }

    /**
     * 获取可分配审核的任务列表
     */
    @GetMapping("/tasks/assignable")
    public Result<List<TaskVO>> getAssignableTasks(
            @RequestParam(required = false) String status) {
        try {
            List<TaskVO> tasks = adminService.getAssignableTasks(status);
            return Result.success("查询成功", tasks);
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取已备案档案库列表
     */
    @GetMapping("/archive")
    public Result<List<ArchiveTaskVO>> getArchiveList(
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String courseName,
            @RequestParam(required = false) String teacherName,
            @RequestParam(required = false) String status) {
        try {
            List<ArchiveTaskVO> archives = adminService.getArchiveList(semester, major, courseName, teacherName, status);
            return Result.success("查询成功", archives);
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 批量导出已备案档案（ZIP）
     */
    @PostMapping("/archive/export")
    public Result<String> exportArchive(@RequestBody List<Long> taskIds, HttpServletResponse response) {
        try {
            String zipFilePath = adminService.exportArchive(taskIds);
            return Result.success("导出成功", zipFilePath);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("导出失败：" + e.getMessage());
        }
    }

    /**
     * 下载导出的ZIP文件
     */
    @GetMapping("/download/{fileName:.+}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        try {
            java.io.File file = new java.io.File(adminService.getDownloadPath(), fileName);
            System.out.println("下载文件: " + file.getAbsolutePath());
            System.out.println("文件存在: " + file.exists());
            System.out.println("文件大小: " + file.length() + " 字节");

            if (!file.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在");
                return;
            }

            response.setContentType("application/zip");
            response.setContentLengthLong(file.length());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + new String(fileName.getBytes("UTF-8"), "ISO-8859-1") + "\"");
            response.setHeader("Access-Control-Allow-Origin", "*");

            try (FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 ServletOutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                out.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                response.getWriter().write("下载失败: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

}
