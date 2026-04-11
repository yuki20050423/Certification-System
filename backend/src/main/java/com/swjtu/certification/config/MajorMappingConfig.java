package com.swjtu.certification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConfigurationProperties(prefix = "major.mapping")
public class MajorMappingConfig {

    private Map<String, List<String>> mappings = new LinkedHashMap<>();

    public Map<String, List<String>> getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, List<String>> mappings) {
        this.mappings = mappings;
    }

    // 手动初始化映射，作为备用方案
    public void initMappings() {
        if (mappings == null || mappings.isEmpty() || mappings.containsKey("0")) {
            System.out.println("=== 初始化默认专业映射 ===");
            mappings = new LinkedHashMap<>();
            mappings.put("计算机科学与技术", Arrays.asList("计算机类", "计算机"));
            mappings.put("软件工程", Arrays.asList("计算机类","软件工程", "软件"));
            mappings.put("网络工程", Arrays.asList("网络工程", "网络"));
            mappings.put("信息安全", Arrays.asList("信息安全", "信安"));
            mappings.put("人工智能", Arrays.asList("人工智能", "AI"));
            mappings.put("数据科学与大数据技术", Arrays.asList("大数据", "数据科学"));
            mappings.put("物联网工程", Arrays.asList("物联网"));
            mappings.put("电子信息工程", Arrays.asList("电子信息", "电信"));
            mappings.put("通信工程", Arrays.asList("通信"));
            mappings.put("自动化", Arrays.asList("自动化"));
            mappings.put("电气工程及其自动化", Arrays.asList("电气"));
            mappings.put("机械工程", Arrays.asList("机械"));
            mappings.put("土木工程", Arrays.asList("土木"));
            mappings.put("交通运输", Arrays.asList("交通运输", "交通"));
            System.out.println("默认映射初始化完成: " + mappings);
        }
    }

    public List<String> getMatchKeywords(String major) {
        // 初始化映射（如果需要）
        initMappings();
        
        System.out.println("=== getMatchKeywords 被调用 ===");
        System.out.println("输入的专业: '" + major + "'");
        System.out.println("当前mappings: " + mappings);
        
        if (major == null || major.trim().isEmpty()) {
            System.out.println("专业为空，返回空列表");
            return Collections.emptyList();
        }

        List<String> keywords = mappings.get(major);
        System.out.println("直接从mappings获取的关键词: " + keywords);
        
        if (keywords != null) {
            System.out.println("找到映射，返回关键词: " + keywords);
            return keywords;
        }

        System.out.println("未找到直接映射，开始遍历查找");
        for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
            System.out.println("  检查键: '" + entry.getKey() + "'，值: " + entry.getValue());
            if (entry.getKey().equals(major)) {
                System.out.println("  找到匹配的键，返回关键词: " + entry.getValue());
                return entry.getValue();
            }
        }

        System.out.println("未找到任何映射，返回原专业: [" + major + "]");
        return Collections.singletonList(major);
    }

    public String findMatchingMajor(String preferred) {
        if (preferred == null || preferred.trim().isEmpty()) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : mappings.entrySet()) {
            String majorName = entry.getKey();
            List<String> keywords = entry.getValue();

            if (keywords != null) {
                for (String keyword : keywords) {
                    if (keyword != null && !keyword.trim().isEmpty()) {
                        if (preferred.contains(keyword)) {
                            return majorName;
                        }
                    }
                }
            }
        }

        return null;
    }
}
