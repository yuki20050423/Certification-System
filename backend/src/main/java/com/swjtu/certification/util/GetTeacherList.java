package com.swjtu.certification.util;

import com.swjtu.certification.entity.Teacher;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Component
public class GetTeacherList {

    /* ========================= 1. 配置信息 ========================= */

    private static final Map<String, String> COOKIES = new HashMap<>() {{
        put("JSESSIONID", "D97186FE2E3A6949C04CC8EC333E88D6");
        put("TWFID", "2d8ab46e05cb3058");
        put("platformMultilingual_-_edu.cn", "zh_CN");
    }};

    private static final Map<String, String> TERM_ID_MAP = buildRecentTermMap();

    private static Map<String, String> buildRecentTermMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("2020-2021-1", "96"); map.put("2020-2021-2", "98");
        map.put("2021-2022-1", "100"); map.put("2021-2022-2", "105");
        map.put("2022-2023-1", "107"); map.put("2022-2023-2", "109");
        map.put("2023-2024-1", "112"); map.put("2023-2024-2", "113");
        map.put("2024-2025-1", "116"); map.put("2024-2025-2", "118");
        map.put("2025-2026-1", "120"); map.put("2025-2026-2", "122");
        map.put("2026-2027-1", "124"); map.put("2026-2027-2", "126");
        return Collections.unmodifiableMap(map);
    }

    /* ========================= 2. 核心抓取逻辑 ========================= */

    public static List<Teacher> getTeachersByAndSemesterCourseName(String semester, String courseName) {
        List<Teacher> result = new ArrayList<>();
        String termId = TERM_ID_MAP.get(semester);
        if (termId == null) {
            System.err.println("错误：未找到学期 [" + semester + "] 对应的 ID");
            return result;
        }

        // 格式化 TermName (例如: 2024-2025第1学期)
        String[] parts = semester.split("-");
        String termName = parts[0] + "-" + parts[1] + "第 " + parts[2] + " 学期";
        String baseUrl = "http://jwc-swjtu-edu-cn.vpn.swjtu.edu.cn:8118/vatuu/CourseAction";

        System.out.println(">>> 启动抓取: 学期=" + semester + ", 课程=" + courseName);
        int page = 1;

        try {
            while (true) {
                // 使用 .data() 自动处理所有编码问题，避免双重转义
                Connection conn = Jsoup.connect(baseUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .cookies(COOKIES)
                        .timeout(12_000)
                        .method(Connection.Method.GET)
                        .data("setAction", "queryCourseList")
                        .data("viewType", "")
                        .data("jumpPage", String.valueOf(page))
                        .data("selectTableType", "History")
                        .data("selectTermId", termId)
                        .data("selectTermName", termName)
                        .data("selectAction", "QueryName")
                        .data("key1", courseName) // 直接传入中文
                        .data("courseType", "all")
                        .data("key4", "")
                        .data("btn_query", "执行查询")
                        .data("orderType", "teachId")
                        .data("orderValue", "asc");

                Document doc = conn.get();

                // 核心 Debug 信息：打印前 100 字确认页面状态
                String preview = doc.text().trim();
                System.out.println("Page " + page + " 预览: " + (preview.length() > 60 ? preview.substring(0, 60) : preview));

                Element table = doc.selectFirst("table");
                if (table == null || doc.text().contains("没有找到相关记录")) {
                    System.out.println("--- 查询结束或无记录 ---");
                    break;
                }

                Elements rows = table.select("tr");
                if (rows.size() < 2) break;

                int pageCount = 0;
                for (int i = 1; i < rows.size(); i++) { // 从 1 开始跳过表头
                    Elements cols = rows.get(i).select("th, td");
                    if (cols.size() < 14) continue;

                    // 再次检查“无记录”行
                    if (cols.get(0).text().contains("没有找到相关记录")) continue;

                    Teacher t = new Teacher();
                    t.setSemester(semester);
                    t.setSelectCode(cols.get(1).text().trim());
                    t.setCourseCode(cols.get(2).text().trim());
                    t.setCourseName(cols.get(3).text().trim());
                    t.setTeachingClass(cols.get(4).text().trim());

                    try {
                        t.setCredits((byte) Float.parseFloat(cols.get(5).text().trim()));
                    } catch (Exception e) {
                        t.setCredits((byte) 0);
                    }

                    t.setNature(cols.get(6).text().trim());
                    t.setDepartment(cols.get(7).text().trim());
                    t.setTeacherName(cols.get(8).text().trim());
                    t.setTitle(cols.get(9).text().trim());
                    t.setTimeLocation(cols.get(10).text().trim());
                    t.setPreferred(cols.get(11).text().trim());
                    t.setStatus(cols.get(12).text().trim());
                    t.setCampus(cols.get(13).text().trim());
                    result.add(t);
                    pageCount++;
                }

                System.out.println("成功抓取第 " + page + " 页，获得 " + pageCount + " 条数据");

                // 翻页判断
                if (!doc.text().contains("下一页") && !doc.text().contains("›")) break;

                page++;
                Thread.sleep(800);
            }
        } catch (Exception e) {
            System.err.println("!!! 抓取异常: " + e.getMessage());
        }

        return result;
    }

    /* ========================= 3. 辅助查询方法 ========================= */

    public static List<Teacher> getTeachersByCourseName(String courseName) {
        List<Teacher> all = new ArrayList<>();
        // 按学期倒序查询，通常最新的更有参考价值
        List<String> semesters = new ArrayList<>(TERM_ID_MAP.keySet());
        Collections.reverse(semesters);

        for (String semester : semesters) {
            all.addAll(getTeachersByAndSemesterCourseName(semester, courseName));
        }
        return all;
    }

    public static List<Teacher> getTeachersByCourseNameCurrent(String courseName) {
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();
        // 交大通常 2-7 月为第 2 学期，8-1 月为下学年的第 1 学期
        int flag = (month >= 2 && month <= 7) ? 2 : 1;
        int baseYear = (flag == 1 && month <= 12 && month >= 8) ? year : (flag == 2 ? year - 1 : year - 1);

        // 简单计算当前学期字符串
        String currentSemester = baseYear + "-" + (baseYear + 1) + "-" + flag;
        return getTeachersByAndSemesterCourseName(currentSemester, courseName);
    }
}