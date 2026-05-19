package com.yangke.forum.module.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangke.forum.module.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM t_user WHERE (username = #{account} OR email = #{account}) AND deleted = 0")
    User selectByAccount(@Param("account") String account);

    @Select("SELECT * FROM t_user WHERE activation_code = #{code} AND deleted = 0")
    User selectByActivationCode(@Param("code") String code);
}
