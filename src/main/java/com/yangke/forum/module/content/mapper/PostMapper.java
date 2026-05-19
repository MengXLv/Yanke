package com.yangke.forum.module.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yangke.forum.module.content.entity.Post;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE t_post SET view_count = view_count + 1 WHERE id = #{postId}")
    int incrementViewCount(@Param("postId") Long postId);

    @Update("UPDATE t_post SET like_count = like_count + #{delta} WHERE id = #{postId}")
    int updateLikeCount(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("UPDATE t_post SET comment_count = comment_count + #{delta} WHERE id = #{postId}")
    int updateCommentCount(@Param("postId") Long postId, @Param("delta") int delta);
}
