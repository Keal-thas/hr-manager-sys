package com.hrmanager.integration.wecom;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("wecom_message_log")
public class WeComMessageLog {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String employeeNo;
    private String wecomUserId;
    private String scene;
    private String title;
    private String content;
    private String status;
    private Integer errcode;
    private String errmsg;
    private LocalDateTime createdAt;
}
