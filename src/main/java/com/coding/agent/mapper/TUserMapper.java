package com.coding.agent.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.coding.agent.domain.po.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TUserMapper extends BaseMapper<User> {

}
