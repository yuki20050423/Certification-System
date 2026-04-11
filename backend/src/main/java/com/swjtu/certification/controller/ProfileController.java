package com.swjtu.certification.controller;

import com.swjtu.certification.dto.ChangePasswordDTO;
import com.swjtu.certification.dto.UpdateProfileDTO;
import com.swjtu.certification.service.UserService;
import com.swjtu.certification.vo.Result;
import com.swjtu.certification.vo.UserProfileVO;
import com.swjtu.certification.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.HashMap;
import com.swjtu.certification.dto.UpdateProfileDTO;

import javax.validation.Valid;

/**
 * 个人中心控制器
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Validated
public class ProfileController {
    @Autowired
    private UserService userService;

    /**
     * 获取用户个人信息
     */

    @GetMapping
    public ResponseEntity<Map<String, Object>> getUserProfile(
            @RequestParam("teacherId") Long userId,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userService.getUserById(userId);
            if (user == null) {
                result.put("code", 404);
                result.put("msg", "用户不存在");
                return ResponseEntity.status(404).body(result);
            }

            // 封装个人信息（姓名、工号、院系、职称、邮箱）
            Map<String, Object> profile = new HashMap<>();
            profile.put("name", user.getRealName());
            profile.put("workNo", user.getWorkId());
            profile.put("department", user.getDepartment());
            profile.put("title", user.getTitle());
            profile.put("email", user.getEmail());
            profile.put("phone", user.getPhone());

            result.put("code", 200);
            result.put("msg", "获取个人信息成功");
            result.put("data", profile);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "获取个人信息失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 更新用户个人信息
     */
    @PutMapping
    public ResponseEntity<Map<String, Object>> updateUserProfile(
            @RequestParam("teacherId") Long userId,
            @RequestBody UpdateProfileDTO updateProfileDTO,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            userService.updateUserProfile(userId, updateProfileDTO);
            result.put("code", 200);
            result.put("msg", "更新个人信息成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "更新个人信息失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestParam("teacherId") Long userId,
            @Valid @RequestBody ChangePasswordDTO changePasswordDTO,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            userService.changePassword(userId, changePasswordDTO);
            result.put("code", 200);
            result.put("msg", "密码修改成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("msg", "修改密码失败：" + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}

