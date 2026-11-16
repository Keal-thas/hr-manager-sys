package com.hrmanager.attendance.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class PenaltyRow {

    private int rowNum;
    private String importType;
    private String code;
    private String employeeNo;
    private String name;
    private Map<String, String> data = new LinkedHashMap<>();
}
