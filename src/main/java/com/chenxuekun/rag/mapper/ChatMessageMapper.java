package com.chenxuekun.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chenxuekun.rag.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
