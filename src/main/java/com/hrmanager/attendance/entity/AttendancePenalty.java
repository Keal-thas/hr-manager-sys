package com.hrmanager.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attendance_penalty")
public class AttendancePenalty {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String importType;
    private String code;
    private String employeeNo;
    private String name;
    private String departmentName;
    private String dataJson;
    private LocalDateTime createdAt;
}
