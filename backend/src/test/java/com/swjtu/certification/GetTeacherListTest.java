package com.swjtu.certification;

import com.swjtu.certification.entity.Teacher;
import com.swjtu.certification.util.GetTeacherList;
import com.swjtu.certification.util.TestTeacher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest
public class GetTeacherListTest {

    @Test
    public void testGetTeachersByAndSemesterCourseName() {
        String semester = "2023-2024-1";
        String courseName = "数据结构";
        List<Teacher> teachers = GetTeacherList.getTeachersByAndSemesterCourseName(semester, courseName);
        System.out.println(teachers.size());
        teachers.forEach(System.out::println);
    }
}
