package com.swjtu.certification.controller;

import com.swjtu.certification.service.CourseService;
import com.swjtu.certification.vo.CourseVO;
import com.swjtu.certification.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程控制器
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CourseController {
    private final CourseService courseService;

    /**
     * 查询可选课程列表
     * @param grade 年级（可选）
     * @param semester 学期（可选）
     * @param status 课程状态（可选）：0-未指派，1-已指派
     * @return 课程列表
     */
    @GetMapping
    public Result<List<CourseVO>> getCourses(
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String semester,
            @RequestParam(required = false) Integer status) {
        try {
            List<CourseVO> courses = courseService.getAvailableCourses(grade, semester, status);
            return Result.success("查询成功", courses);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}

