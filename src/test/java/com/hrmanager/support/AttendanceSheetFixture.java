package com.hrmanager.support;

import com.hrmanager.attendance.service.AttendanceExcelService;

public final class AttendanceSheetFixture {

    private static final String[] PENALTY_HEADERS = {
            "导入方式", "编号", "工号", "姓名", "部门", "开单日期", "执行月份", "惩罚种类",
            "处罚等级", "处罚层级", "文件制度", "条例条款", "原因", "建议事业部", "建议人",
            "无绩效奖或表现奖月数", "绩效奖点数"
    };

    private AttendanceSheetFixture() {
    }

    public static String attendanceHeader() {
        return String.join(",", AttendanceExcelService.HEADERS);
    }

    public static String attendanceRow(String employeeNo, String name, String department) {
        return attendanceRow(employeeNo, name, department, "0", "0");
    }

    public static String attendanceRow(String employeeNo, String name, String department,
                                       String absentDays, String overtimeHours) {
        return join(attendanceValues(employeeNo, name, department, absentDays, overtimeHours));
    }

    public static Object[] attendanceValues(String employeeNo, String name, String department,
                                            String absentDays, String overtimeHours) {
        Object[] values = new Object[AttendanceExcelService.HEADERS.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = "0";
        }
        values[0] = employeeNo;
        values[1] = name;
        values[2] = department;
        values[3] = "月薪";
        values[4] = absentDays;
        values[6] = overtimeHours;
        values[8] = "22";
        values[9] = "22";
        values[17] = "22";
        values[14] = "";
        values[33] = "";
        values[34] = "";
        return values;
    }

    public static String penaltyHeader() {
        return String.join(",", PENALTY_HEADERS);
    }

    public static String[] penaltyHeaderValues() {
        return PENALTY_HEADERS.clone();
    }

    public static String newPenaltyRow(String employeeNo, String name, String reason) {
        return penaltyRow("新增", "", employeeNo, name, reason);
    }

    public static String modifyPenaltyRow(String code, String employeeNo, String name, String reason) {
        return penaltyRow("修改", code, employeeNo, name, reason);
    }

    public static String penaltyRow(String importType, String code, String employeeNo, String name,
                                    String reason) {
        return join(penaltyValues(importType, code, employeeNo, name, reason));
    }

    public static String[] penaltyValues(String importType, String code, String employeeNo, String name,
                                         String reason) {
        String[] values = new String[PENALTY_HEADERS.length];
        values[0] = importType;
        values[1] = code;
        values[2] = employeeNo;
        values[3] = name;
        values[4] = "";
        values[5] = "2026-01-05";
        values[6] = "2026-01";
        values[7] = "考核";
        values[8] = "A";
        values[9] = "一级";
        values[10] = "员工手册";
        values[11] = "第1条";
        values[12] = reason;
        values[13] = "";
        values[14] = "";
        values[15] = "0";
        values[16] = "0";
        return values;
    }

    private static String join(Object[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values[i]);
        }
        return builder.toString();
    }
}
