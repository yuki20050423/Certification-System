package com.swjtu.certification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swjtu.certification.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户Mapper接口
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

