package com.swjtu.certification.util;

import com.swjtu.certification.entity.Course;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GetCourseList {

    public ArrayList<Course> standardizeDirectory(String dirPath, String charsetName) {
        ArrayList<Course> allCourses = new ArrayList<>();

        Path dir = Paths.get(dirPath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("输入路径不是目录: " + dirPath);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.csv")) {
            for (Path csv : stream) {
                ArrayList<Course> list = standardizeFile(csv, charsetName);
                allCourses.addAll(list);
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        System.out.println("目录处理完成，共计课程数：" + allCourses.size());
        return allCourses;
    }

    public ArrayList<Course> standardizeFile(Path filePath, String charsetName) throws IOException {
        ArrayList<Course> courses = new ArrayList<>();

        String fileName = filePath.getFileName().toString();
        String year = extractYear(fileName);
        String major = extractMajor(fileName);

        Pattern natureOk = Pattern.compile("^(必修|选修|限修).*$");
        Pattern dataRow = Pattern.compile("^\"[^\"]+\",\"[^\"]+\",\"[^\"]+\",\"\\d+(\\.\\d+)?\".*");

        try (BufferedReader br = Files.newBufferedReader(filePath, Charset.forName(charsetName))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || !dataRow.matcher(line).matches()) continue;

                String[] f = line.split("\",\"", -1);
                if (f.length != 9) continue;
                for (int i = 0; i < f.length; i++) {
                    f[i] = f[i].replaceAll("^\"|\"$", "").trim();
                }

                if (!natureOk.matcher(f[2]).find()) continue;

                f[0] = removeEnglish(f[0]);
                f[1] = processCourseName(f[1]);
                f[2] = removeEnglish(f[2]);
                f[3] = formatCredit(f[3]);
                f[4] = formatCredit(f[4]);
                f[5] = extractSemester(f[5]);
                f[6] = removeEnglish(f[6]);
                f[7] = cleanCell(f[7]);
                f[8] = cleanCell(f[8]);

                Course c = new Course();
                c.setCourseName(f[1]);
                c.setGrade(year);
                c.setMajor(major);
                c.setSemester(f[5]);
                c.setStatus(0);
                c.setCreateTime(java.time.LocalDateTime.now());
                c.setUpdateTime(java.time.LocalDateTime.now());

                courses.add(c);
            }
        }

        System.out.println("已处理文件：" + filePath.getFileName() + "（课程数：" + courses.size() + "）");
        return courses;
    }

    private static String removeEnglish(String text) {
        if (text == null || "null".equals(text)) return "null";
        text = text.replace('\u00A0', ' ');
        int cut = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) {
                cut = i;
                break;
            }
        }
        if (cut != -1) text = text.substring(0, cut);
        text = text.replaceAll("[（(].*[）)]$", "")
                .replaceAll("[,;:.\\-_/]+$", "")
                .trim();
        return text.isEmpty() ? "null" : text;
    }

    private static String extractYear(String fileName) {
        Matcher m = Pattern.compile("\\d{4}").matcher(fileName);
        return m.find() ? m.group(0) : "未知";
    }

    private static String extractMajor(String fileName) {
        String name = fileName;
        name = name.replaceAll("^\\d{4}", "");
        name = name.replaceAll("培养方案", "");
        name = name.replaceAll("专业", "");
        name = name.replaceAll("\\.(csv|docx|doc|xlsx|xls)$", "");
        name = name.replaceAll("^[\\s_\\-]+|[\\s_\\-]+$", "");
        name = name.trim();
        return name.isEmpty() ? null : name;
    }

    private static String extractSemester(String s) {
        if ("null".equals(s)) return "null";
        return s.replaceAll("学期.*$", "学期");
    }

    private static String cleanCell(String cell) {
        if ("null".equals(cell)) return "null";
        String s = cell.replaceAll("（[^）]*）$", "");
        s = s.replaceAll("(?i)[a-z].*$", "");
        return s.trim();
    }

    private static String formatCredit(String s) {
        if ("null".equals(s)) return "null";
        try {
            return String.format("%.1f", Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return "null";
        }
    }

    private static String generateCourseCode(String courseName, String year) {
        if (courseName == null || courseName.isEmpty()) {
            return "C" + System.currentTimeMillis();
        }
        String code = courseName.replaceAll("[^\\u4e00-\\u9fa5]", "");
        if (code.length() > 4) {
            code = code.substring(0, 4);
        }
        return year + code;
    }

    private static final Pattern COURSE_PATTERN = Pattern.compile(
            "^(.*?)(?=[A-Z][a-z]{2,}|(?<=[0-9A-ZⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ])\\s+[A-Z][a-z])"
    );

    private static String processCourseName(String courseName) {
        if (courseName == null || "null".equals(courseName)) return "null";
        Matcher matcher = COURSE_PATTERN.matcher(courseName);
        String result;
        if (matcher.find()) {
            result = matcher.group(1);
        } else {
            result = courseName;
        }
        return result.replaceAll("[\\s\\u3000\\u00A0]+", "");
    }
}
