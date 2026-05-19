package com.yangke.forum.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangke.forum.module.content.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
}
