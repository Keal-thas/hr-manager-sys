package com.hrmanager.attendance.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AttendanceRow {

    private int rowNum;
    private String employeeNo;
    private String name;
    private Map<String, String> data = new LinkedHashMap<>();
}
