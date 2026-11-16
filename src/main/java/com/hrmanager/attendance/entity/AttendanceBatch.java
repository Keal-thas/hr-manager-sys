package com.hrmanager.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attendance_batch")
public class AttendanceBatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer batchYear;
    private Integer batchMonth;
    private String status;
    private String uploadMode;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Boolean latest;
}
