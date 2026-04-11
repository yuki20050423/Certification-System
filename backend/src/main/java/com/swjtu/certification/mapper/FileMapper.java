package com.swjtu.certification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swjtu.certification.entity.File;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件Mapper接口
 */
@Mapper
public interface FileMapper extends BaseMapper<File> {
}

