package com.swjtu.certification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swjtu.certification.entity.ReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审核记录Mapper接口
 */
@Mapper
public interface ReviewRecordMapper extends BaseMapper<ReviewRecord> {
    @Select("SELECT * FROM review_record WHERE task_id = #{taskId} ORDER BY create_time DESC")
    List<ReviewRecord> selectByTaskIdOrderByCreateTimeDesc(@Param("taskId") Long taskId);
}

