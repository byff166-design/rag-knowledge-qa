package com.chenxuekun.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb")
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createTime;
}
