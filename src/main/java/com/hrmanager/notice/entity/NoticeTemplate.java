package com.hrmanager.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice_template")
public class NoticeTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String status;
    private LocalDateTime createdAt;
}
