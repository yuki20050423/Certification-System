package com.swjtu.certification.controller;

import com.swjtu.certification.service.UserService;
import com.swjtu.certification.vo.Result;
import com.swjtu.certification.vo.TeacherVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户控制器（教师和审核员相关接口）
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {
    private final UserService userService;

    /**
     * 查询教师列表
     * @param code 工号（可选）
     * @param name 姓名（可选）
     * @return 教师列表
     */
    @GetMapping("/teachers")
    public Result<List<TeacherVO>> getTeachers(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name) {
        try {
            List<TeacherVO> teachers = userService.getTeachers(code, name);
            return Result.success("查询成功", teachers);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询可选审核员列表
     * @param id 要排除的教师ID（可选）
     * @return 审核员列表
     */
    @GetMapping("/assessors")
    public Result<List<TeacherVO>> getAssessors(
            @RequestParam(required = false) Long id) {
        try {
            List<TeacherVO> assessors = userService.getAssessors(id);
            return Result.success("查询成功", assessors);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}

