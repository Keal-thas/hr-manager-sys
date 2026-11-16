package com.hrmanager.notice.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("message_notice")
public class MessageNotice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String content;
    private String targetType;
    private String targetIds;
    private Long publisherId;
    private String status;
    private LocalDateTime createdAt;
}
