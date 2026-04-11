package com.swjtu.certification.util;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

public class SemesterUtils {

    private static final Map<String, String> SEMESTER_DISPLAY_MAP = new HashMap<>();
    private static final Map<String, String> DISPLAY_SEMESTER_MAP = new HashMap<>();

    static {
        SEMESTER_DISPLAY_MAP.put("1", "春季");
        SEMESTER_DISPLAY_MAP.put("2", "秋季");

        DISPLAY_SEMESTER_MAP.put("春季", "1");
        DISPLAY_SEMESTER_MAP.put("秋季", "2");
    }

    private static final Map<String, String> SEMESTER_EXPORT_MAP = new HashMap<>();
    static {
        SEMESTER_EXPORT_MAP.put("1", "第一学期");
        SEMESTER_EXPORT_MAP.put("2", "第二学期");
    }

    public static String getCurrentSemester() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        int flag = (month >= 2 && month <= 7) ? 2 : 1;
        int baseYear = flag == 2 ? year : year - 1;
        return baseYear + "-" + (baseYear + 1) + "-" + flag;
    }

    public static String getCurrentYear() {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        int flag = (month >= 2 && month <= 7) ? 2 : 1;
        return flag == 2 ? String.valueOf(year) : String.valueOf(year - 1);
    }

    public static String toStandardFormat(String year, String semesterDisplay) {
        if (year == null || semesterDisplay == null) {
            return null;
        }
        
        String semesterFlag = DISPLAY_SEMESTER_MAP.get(semesterDisplay);
        if (semesterFlag == null) {
            return null;
        }
        
        try {
            int baseYear = Integer.parseInt(year);
            return baseYear + "-" + (baseYear + 1) + "-" + semesterFlag;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toDisplayFormat(String standardSemester) {
        if (standardSemester == null || !standardSemester.matches("\\d{4}-\\d{4}-[12]")) {
            return standardSemester;
        }
        
        String[] parts = standardSemester.split("-");
        String semesterFlag = parts[2];
        String semesterDisplay = SEMESTER_DISPLAY_MAP.get(semesterFlag);
        
        if (semesterDisplay == null) {
            return standardSemester;
        }
        
        return parts[0] + "-" + parts[1] + " " + semesterDisplay;
    }

    public static String extractYear(String standardSemester) {
        if (standardSemester == null || !standardSemester.matches("\\d{4}-\\d{4}-[12]")) {
            return null;
        }
        String[] parts = standardSemester.split("-");
        return parts[0];
    }

    public static String extractSemesterDisplay(String standardSemester) {
        if (standardSemester == null || !standardSemester.matches("\\d{4}-\\d{4}-[12]")) {
            return null;
        }
        String[] parts = standardSemester.split("-");
        String semesterFlag = parts[2];
        return SEMESTER_DISPLAY_MAP.get(semesterFlag);
    }

    public static String getSemesterFlag(String semesterDisplay) {
        return DISPLAY_SEMESTER_MAP.get(semesterDisplay);
    }

    public static boolean isValidStandardFormat(String semester) {
        return semester != null && semester.matches("\\d{4}-\\d{4}-[12]");
    }

    public static String getNextSemester(String currentSemester) {
        if (!isValidStandardFormat(currentSemester)) {
            return getCurrentSemester();
        }
        
        String[] parts = currentSemester.split("-");
        int baseYear = Integer.parseInt(parts[0]);
        int flag = Integer.parseInt(parts[2]);
        
        if (flag == 1) {
            return baseYear + "-" + (baseYear + 1) + "-2";
        } else {
            return (baseYear + 1) + "-" + (baseYear + 2) + "-1";
        }
    }

    public static String getPreviousSemester(String currentSemester) {
        if (!isValidStandardFormat(currentSemester)) {
            return getCurrentSemester();
        }

        String[] parts = currentSemester.split("-");
        int baseYear = Integer.parseInt(parts[0]);
        int flag = Integer.parseInt(parts[2]);

        if (flag == 2) {
            return baseYear + "-" + (baseYear + 1) + "-1";
        } else {
            return (baseYear - 1) + "-" + baseYear + "-2";
        }
    }

    /**
     * 将标准格式学期转换为导出格式（用于文件名、文件夹名）
     * 例如: 2025-2026-2 → 2025-2026学年第二学期
     * @param standardSemester 标准格式学期 (如 2025-2026-2)
     * @return 导出格式学期 (如 2025-2026学年第二学期)
     */
    public static String toExportFormat(String standardSemester) {
        if (standardSemester == null || !standardSemester.matches("\\d{4}-\\d{4}-[12]")) {
            return standardSemester;
        }

        String[] parts = standardSemester.split("-");
        String semesterFlag = parts[2];
        String semesterDisplay = SEMESTER_EXPORT_MAP.get(semesterFlag);

        if (semesterDisplay == null) {
            return standardSemester;
        }

        return parts[0] + "-" + parts[1] + "学年" + semesterDisplay;
    }

    /**
     * 将导出格式学期转换为标准格式（用于数据库查询）
     * 例如: 2025-2026学年第二学期 → 2025-2026-2
     * @param exportSemester 导出格式学期 (如 2025-2026学年第二学期)
     * @return 标准格式学期 (如 2025-2026-2)，如果无法转换则返回原值
     */
    public static String fromExportFormat(String exportSemester) {
        if (exportSemester == null || !exportSemester.matches("\\d{4}-\\d{4}学年.*")) {
            return exportSemester;
        }

        // 提取学年部分和学期部分
        String[] parts = exportSemester.split("学年");
        if (parts.length < 2) {
            return exportSemester;
        }

        String academicYear = parts[0]; // 2025-2026
        String semesterText = parts[1]; // 第二学期

        // 反向查找学期标志
        for (Map.Entry<String, String> entry : SEMESTER_EXPORT_MAP.entrySet()) {
            if (semesterText.equals(entry.getValue())) {
                return academicYear + "-" + entry.getKey();
            }
        }

        return exportSemester;
    }
}
