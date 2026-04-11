package com.swjtu.certification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swjtu.certification.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程Mapper接口
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}

