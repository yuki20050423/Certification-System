package com.swjtu.certification.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MajorMappingConfigTest {

    @Autowired
    private MajorMappingConfig majorMappingConfig;

    @Test
    public void testMappings() {
        System.out.println("=== 测试专业映射配置 ===");
        System.out.println("配置的所有映射: " + majorMappingConfig.getMappings());
        System.out.println("计算机科学与技术的映射: " + majorMappingConfig.getMatchKeywords("计算机科学与技术"));
    }
}
