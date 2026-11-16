package com.hrmanager.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("attendance_record")
public class AttendanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private String employeeNo;
    private Long userId;
    private String name;
    private String departmentName;
    private String status;

    @JsonIgnore
    private String signatureImage;

    private LocalDateTime signatureTime;
    private Boolean agree;
    private String dataJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
