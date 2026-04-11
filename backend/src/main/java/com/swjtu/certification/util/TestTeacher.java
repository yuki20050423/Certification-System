package com.swjtu.certification.util;

import com.swjtu.certification.entity.Teacher;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TestTeacher {

    /* ========================= 1. 配置信息 ========================= */

    // 从你提供的截图中提取的 Cookie 信息
    private static final Map<String, String> COOKIES = new HashMap<>() {{
        //put("JSESSIONID", "86052B6EDB89AEC25C4912921547B9E0");
        //put("HMACCOUNT", "2ADF566D31796646");
        // 百度统计相关的 Cookie 一般不校验，如需添加可取消注释：
        // put("Hm_lvt_87cf2c3472ff749fe7d2282b7106e8f1", "1769157897");
        // put("Hm_lpvt_87cf2c3472ff749fe7d2282b7106e8f1", "1769157898");

        put("JSESSIONID", "86052B6EDB89AEC25C4912921547B9E0");
        put("TWFID", "2f403939e8ab585f");
        put("HMACCOUNT_-_jwc.swjtu.edu.cn", "9BA483D2D3E33319");
        put("happyVoyage_-_cas.swjtu.edu.cn", "hpE0EOXTSrhkqi7KMKKK2XHscwNVcRE0gGH828UO6cy...");
        put("platformMultilingual_-_edu.cn", "zh_CN");
    }};

    private static final Map<String, String> TERM_ID_MAP = buildRecentTermMap();

    /** 按时间倒序生成最近的学期映射 */
    private static Map<String, String> buildRecentTermMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("2020-2021-1", "96");
        map.put("2020-2021-2", "98");
        map.put("2021-2022-1", "100");
        map.put("2021-2022-2", "105");
        map.put("2022-2023-1", "107");
        map.put("2022-2023-2", "109");
        map.put("2023-2024-1", "112");
        map.put("2023-2024-2", "113");
        map.put("2024-2025-1", "116");
        map.put("2024-2025-2", "118");
        map.put("2025-2026-1", "120");
        map.put("2025-2026-2", "122");
        map.put("2026-2027-1", "124");
        map.put("2026-2027-2", "126");
        return Collections.unmodifiableMap(map);
    }

    /* ========================= 2. 核心抓取 ========================= */

    public static List<Teacher> getTeachersByAndSemesterCourseName(String semester, String courseName) {
        List<Teacher> result = new ArrayList<>();
        String termId = TERM_ID_MAP.get(semester);
        if (termId == null) return result;

        String[] parts = semester.split("-");
        String termName = parts[0] + "-" + parts[1] + "第" + parts[2] + "学期";
        //http://jwc-swjtu-edu-cn.vpn.swjtu.edu.cn:8118/vatuu/CourseAction?setAction=queryCourseList&selectTableType=History
        //String baseUrl = "http://jwc.swjtu.edu.cn/vatuu/CourseAction";
        String baseUrl = "http://jwc-swjtu-edu-cn.vpn.swjtu.edu.cn:8118/vatuu/CourseAction";
        String btnQuery = URLEncoder.encode("执行查询", StandardCharsets.UTF_8);
        int page = 1;

        try {
            int cnt = 0;
            while (true) {
                String query = String.format(
                        "?setAction=queryCourseList&viewType=&jumpPage=%d"
                                + "&selectTableType=History&selectTermId=%s&selectTermName=%s"
                                + "&selectAction=QueryName&key1=%s&courseType=all&key4=&btn_query=%s"
                                + "&orderType=teachId&orderValue=asc",
                        page,
                        URLEncoder.encode(termId, StandardCharsets.UTF_8),
                        URLEncoder.encode(termName, StandardCharsets.UTF_8),
                        URLEncoder.encode(courseName, StandardCharsets.UTF_8),
                        btnQuery
                );

                // --- 核心改动点：链式调用 .cookies(COOKIES) ---
                Document doc = Jsoup.connect(baseUrl + query)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .cookies(COOKIES) // 注入 Cookie
                        .timeout(10_000)
                        .get();

                System.out.println("--- 调试：页面主体内容开始 ---");
                String bodyHtml = doc.body().html();
                if (bodyHtml.length() > 50000) {
                    System.out.println(bodyHtml.substring(0, 50000)); // 只打印前500字，防止刷屏
                } else {
                    System.out.println(bodyHtml);
                }
                System.out.println("--- 调试：页面主体内容结束 ---");

                System.out.println("正在请求: " + baseUrl + query);

                Element table = doc.selectFirst("table");
                if (table == null) break;
                Elements rows = table.select("tr");
                if (rows.size() < 2) break;

                boolean firstRow = true;
                for (Element row : rows) {
                    if (firstRow) { firstRow = false; continue; }
                    Elements cols = row.select("th, td");
                    if (cols.size() < 14) continue;
                    if (cols.get(0).text().contains("没有找到相关记录")) continue;

                    Teacher t = new Teacher();
                    t.setSemester(semester);
                    t.setSelectCode(cols.get(1).text().trim());
                    t.setCourseCode(cols.get(2).text().trim());
                    t.setCourseName(cols.get(3).text().trim());
                    t.setTeachingClass(cols.get(4).text().trim());

                    // 简单鲁棒性处理：防止分数为非数字导致崩溃
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
                    System.out.println(cnt++);
                }

                // 判断翻页
                if (!doc.text().contains("下一页") && !doc.text().contains("›")) break;
                page++;
                Thread.sleep(800); // 稍微降低一点延迟
            }
        } catch (Exception e) {
            System.err.println("抓取过程中出现异常: " + e.getMessage());
            e.printStackTrace();
        }
        return result;
    }
}