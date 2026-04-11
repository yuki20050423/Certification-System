package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.entity.Course;
import com.swjtu.certification.mapper.CourseMapper;
import com.swjtu.certification.vo.CourseVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程服务类
 */
@Service
@RequiredArgsConstructor
public class CourseService {
    private final CourseMapper courseMapper;

    /**
     * 查询可选课程列表
     * @param grade 年级（可选）
     * @param semester 学期（可选）
     * @param status 课程状态（可选）：0-未指派，1-已指派
     * @return 课程列表
     */
    public List<CourseVO> getAvailableCourses(String grade, String semester, Integer status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
        
        // 按年级筛选
        if (grade != null && !grade.trim().isEmpty()) {
            wrapper.eq(Course::getGrade, grade);
        }
        
        // 按学期筛选
        if (semester != null && !semester.trim().isEmpty()) {
            wrapper.eq(Course::getSemester, semester);
        }
        
        // 按状态筛选
        if (status != null) {
            wrapper.eq(Course::getStatus, status);
        }
        
        List<Course> courses = courseMapper.selectList(wrapper);
        
        return courses.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 根据ID获取课程
     */
    public Course getCourseById(Long id) {
        return courseMapper.selectById(id);
    }

    /**
     * 更新课程状态
     */
    public void updateCourseStatus(Long courseId, Integer status) {
        Course course = courseMapper.selectById(courseId);
        if (course != null) {
            course.setStatus(status);
            courseMapper.updateById(course);
        }
    }

    /**
     * 转换为VO
     */
    private CourseVO convertToVO(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setCourseName(course.getCourseName());
        vo.setGrade(course.getGrade());
        vo.setMajor(course.getMajor());
        vo.setSemester(course.getSemester());
        vo.setStatus(course.getStatus());
        vo.setStatusDesc(course.getStatus() == 1 ? "已指派" : "未指派");
        return vo;
    }
}

