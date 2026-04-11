package com.swjtu.certification.controller;

import com.swjtu.certification.dto.LoginDTO;
import com.swjtu.certification.dto.RegisterDTO;
import com.swjtu.certification.service.UserService;
import com.swjtu.certification.vo.LoginVO;
import com.swjtu.certification.vo.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 允许跨域，生产环境应配置具体域名
public class AuthController {
    private final UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        try {
            LoginVO loginVO = userService.login(loginDTO);
            return Result.success("登录成功", loginVO);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Object> register(@Valid @RequestBody RegisterDTO registerDTO) {
        try {
            userService.register(registerDTO);
            return Result.success("注册成功");
        } catch (Exception e) {
            // 返回详细错误信息
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            return Result.error(400, e.getMessage());
        }
    }
}

