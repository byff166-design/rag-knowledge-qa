package com.chenxuekun.rag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String fileName;
    /** PENDING / PROCESSING / DONE / FAILED */
    private String status;
    private Integer chunkCount;
    private LocalDateTime createTime;
}
