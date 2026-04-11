package com.swjtu.certification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.swjtu.certification.dto.ChangePasswordDTO;
import com.swjtu.certification.dto.LoginDTO;
import com.swjtu.certification.dto.RegisterDTO;
import com.swjtu.certification.dto.UpdateProfileDTO;
import com.swjtu.certification.entity.User;
import com.swjtu.certification.mapper.UserMapper;
import com.swjtu.certification.vo.LoginVO;
import com.swjtu.certification.vo.TeacherVO;
import com.swjtu.certification.vo.UserProfileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 用户注册
     */
    @Transactional
    public void register(RegisterDTO registerDTO) {
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> usernameWrapper = new LambdaQueryWrapper<>();
        usernameWrapper.eq(User::getUsername, registerDTO.getUsername());
        if (userMapper.selectCount(usernameWrapper) > 0) {
            throw new RuntimeException("用户名已存在");
        }

        // 检查工号是否已存在
        LambdaQueryWrapper<User> workIdWrapper = new LambdaQueryWrapper<>();
        workIdWrapper.eq(User::getWorkId, registerDTO.getWorkId());
        if (userMapper.selectCount(workIdWrapper) > 0) {
            throw new RuntimeException("工号已存在");
        }

        // 检查邮箱是否已存在
        LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
        emailWrapper.eq(User::getEmail, registerDTO.getEmail());
        if (userMapper.selectCount(emailWrapper) > 0) {
            throw new RuntimeException("邮箱已被注册");
        }

        // 检查手机号是否已存在
        LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(User::getPhone, registerDTO.getPhone());
        if (userMapper.selectCount(phoneWrapper) > 0) {
            throw new RuntimeException("手机号已被注册");
        }

        // 验证密码一致性
        if (!registerDTO.getPassword().equals(registerDTO.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 创建新用户
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setWorkId(registerDTO.getWorkId());
        user.setRealName(registerDTO.getRealName());
        user.setEmail(registerDTO.getEmail());
        user.setPhone(registerDTO.getPhone());
        user.setRole(registerDTO.getRole());
        user.setPassword(passwordEncoder.encode(registerDTO.getPassword()));
//        user.setRole("USER");
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        userMapper.insert(user);
    }

    /**
     * 用户登录
     */
    public LoginVO login(LoginDTO loginDTO) {
        // 根据用户名或工号查询用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(User::getUsername, loginDTO.getUsername())
                .or()
                .eq(User::getWorkId, loginDTO.getUsername()));
        wrapper.eq(User::getStatus, 1);

        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

        // 生成Token（实际项目中应使用JWT）
        String token = UUID.randomUUID().toString().replace("-", "");

        // 构建响应对象
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);

        LoginVO.UserInfo userInfo = new LoginVO.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setWorkId(user.getWorkId());
        userInfo.setRealName(user.getRealName());
        userInfo.setEmail(user.getEmail());
        userInfo.setPhone(user.getPhone());
        userInfo.setRole(user.getRole());
        loginVO.setUserInfo(userInfo);
        
        // 根据角色设置跳转路径
        if ("ADMIN".equalsIgnoreCase(user.getRole())) {
            loginVO.setRedirectUrl("admin.html");
        } else {
            loginVO.setRedirectUrl("index.html");
        }

        return loginVO;
    }

    /**
     * 查询教师列表
     * @param code 工号（可选，用于搜索）
     * @param name 姓名（可选，用于搜索）
     * @return 教师列表
     */
    public List<TeacherVO> getTeachers(String code, String name) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1);
        wrapper.eq(User::getRole, "TEACHER");

        // 按工号搜索
        if (StringUtils.hasText(code)) {
            wrapper.like(User::getWorkId, code);
        }

        // 按姓名搜索
        if (StringUtils.hasText(name)) {
            wrapper.like(User::getRealName, name);
        }

        List<User> users = userMapper.selectList(wrapper);
        return users.stream().map(this::convertToTeacherVO).collect(Collectors.toList());
    }

    /**
     * 查询可选审核员列表（排除指定的教师ID）
     * @param excludeTeacherId 要排除的教师ID（可选）
     * @return 审核员列表
     */
    public List<TeacherVO> getAssessors(Long excludeTeacherId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getStatus, 1);
        wrapper.eq(User::getRole, "ASSESSOR");

        // 排除指定的教师ID
        if (excludeTeacherId != null) {
            wrapper.ne(User::getId, excludeTeacherId);
        }

        List<User> users = userMapper.selectList(wrapper);
        return users.stream().map(this::convertToTeacherVO).collect(Collectors.toList());
    }

    /**
     * 转换为教师VO
     */
    private TeacherVO convertToTeacherVO(User user) {
        TeacherVO vo = new TeacherVO();
        vo.setId(user.getId());
        vo.setWorkId(user.getWorkId());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        return vo;
    }

    /**
     * 获取用户个人信息
     */
    public UserProfileVO getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return convertToUserProfileVO(user);
    }

    /**
     * 更新用户个人信息
     */
    @Transactional
    public void updateUserProfile(Long userId, UpdateProfileDTO updateProfileDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 如果更新邮箱，检查邮箱是否已被其他用户使用
        if (updateProfileDTO.getEmail() != null && !updateProfileDTO.getEmail().equals(user.getEmail())) {
            LambdaQueryWrapper<User> emailWrapper = new LambdaQueryWrapper<>();
            emailWrapper.eq(User::getEmail, updateProfileDTO.getEmail());
            emailWrapper.ne(User::getId, userId);
            if (userMapper.selectCount(emailWrapper) > 0) {
                throw new RuntimeException("邮箱已被其他用户使用");
            }
        }

        // 如果更新手机号，检查手机号是否已被其他用户使用
        if (updateProfileDTO.getPhone() != null && !updateProfileDTO.getPhone().equals(user.getPhone())) {
            LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(User::getPhone, updateProfileDTO.getPhone());
            phoneWrapper.ne(User::getId, userId);
            if (userMapper.selectCount(phoneWrapper) > 0) {
                throw new RuntimeException("手机号已被其他用户使用");
            }
        }

        // 更新用户信息
        if (updateProfileDTO.getRealName() != null) {
            user.setRealName(updateProfileDTO.getRealName());
        }
        if (updateProfileDTO.getEmail() != null) {
            user.setEmail(updateProfileDTO.getEmail());
        }
        if (updateProfileDTO.getPhone() != null) {
            user.setPhone(updateProfileDTO.getPhone());
        }
        if (updateProfileDTO.getDepartment() != null) {
            user.setDepartment(updateProfileDTO.getDepartment());
        }
        if (updateProfileDTO.getTitle() != null) {
            user.setTitle(updateProfileDTO.getTitle());
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordDTO changePasswordDTO) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(changePasswordDTO.getOldPassword(), user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }

        // 验证新密码和确认密码是否一致
        if (!changePasswordDTO.getNewPassword().equals(changePasswordDTO.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }

        // 验证新密码不能与旧密码相同
        if (passwordEncoder.matches(changePasswordDTO.getNewPassword(), user.getPassword())) {
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(changePasswordDTO.getNewPassword()));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 转换为用户个人信息VO
     */
    private UserProfileVO convertToUserProfileVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setWorkId(user.getWorkId());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setDepartment(user.getDepartment());
        vo.setTitle(user.getTitle());
        vo.setRole(user.getRole());
        return vo;
    }

    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }
}

