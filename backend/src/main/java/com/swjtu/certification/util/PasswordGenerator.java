package com.swjtu.certification.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类 - 用于生成和验证BCrypt密码哈希
 * 这个类主要用于生成测试数据的密码哈希值
 */
public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成密码123456的BCrypt哈希
        String password = "123456";
        String hashedPassword = encoder.encode(password);
        
        System.out.println("原始密码: " + password);
        System.out.println("BCrypt哈希值: " + hashedPassword);
        
        // 验证密码
        boolean matches = encoder.matches(password, hashedPassword);
        System.out.println("密码验证结果: " + matches);
        
        // 验证数据库中的旧哈希值
        String oldHash = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iwy8pQ5O";
        boolean oldMatches = encoder.matches(password, oldHash);
        System.out.println("\n旧哈希值验证结果: " + oldMatches);
        
        // 如果旧哈希值不匹配，输出新的哈希值用于更新数据库
        if (!oldMatches) {
            System.out.println("\n=== 请使用以下SQL更新数据库中的密码 ===");
            System.out.println("UPDATE user SET password = '" + hashedPassword + "' WHERE username = 'admin';");
            System.out.println("UPDATE user SET password = '" + hashedPassword + "' WHERE username = 'teacher01';");
        }
    }
}

