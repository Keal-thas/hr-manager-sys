package com.hrmanager.attendance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmanager.attendance.dto.AttendanceRow;
import com.hrmanager.attendance.dto.PenaltyRow;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class AttendanceExcelService {

    private final ObjectMapper objectMapper;

    public static final String[] HEADERS = {
            "工号", "姓名", "部门", "计资方式", "工作日缺勤天数", "加点小时数", "加班小时数",
            "法定节假日加班小时", "计资类型变化/计件时应出勤天数", "实际出勤天数", "高温天数",
            "病假天数", "工伤假天数", "福利假天数合计-FL", "福利假天数备注",
            "入厂之前或离职之后当月工作日天数", "与加班冲抵后的缺勤天数+FL天数+DA",
            "应出勤天数", "已调休天数", "本月年休天数", "当月加班调休小时数", "旷工天数",
            "迟到10分钟含以内", "早退10分钟含以内", "迟到10-30分钟含", "早退10-30分钟含",
            "迟到30分钟以上", "早退30分钟以上", "个人原因打卡/签到异常次数",
            "下浮绩效点数(一级)", "减少表现奖分数(一级)", "下浮绩效点数(其他)",
            "减少表现奖分数(其他)", "员工本人签字确认"
    };

    private static final Set<String> REQUIRED = new HashSet<>(Arrays.asList(
            "工号", "姓名", "计资方式", "工作日缺勤天数", "实际出勤天数", "应出勤天数"
    ));

    private static final Set<String> TEXT_HEADERS = new HashSet<>(Arrays.asList(
            "工号", "姓名", "部门", "计资方式", "福利假天数备注", "员工本人签字确认"
    ));

    private static final String[] PENALTY_HEADERS = {
            "导入方式", "编号", "工号", "姓名", "部门", "开单日期", "执行月份", "惩罚种类",
            "处罚等级", "处罚层级", "文件制度", "条例条款", "原因", "建议事业部", "建议人",
            "无绩效奖或表现奖月数", "绩效奖点数"
    };

    public List<AttendanceRow> parseAttendance(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            HeaderInfo header = findHeader(sheet, this::canonicalAttendance);
            if (header == null) {
                throw new BusinessException("未找到考勤表头，请使用系统模板");
            }
            DataFormatter formatter = new DataFormatter();
            List<AttendanceRow> rows = new ArrayList<>();
            for (int r = header.row + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> data = readRow(row, header.columns, formatter);
                String employeeNo = data.get("工号");
                String name = data.get("姓名");
                if (!StringUtils.hasText(employeeNo) && !StringUtils.hasText(name)) {
                    continue;
                }
                if (!StringUtils.hasText(employeeNo)) {
                    throw new BusinessException("第" + (r + 1) + "行工号为空");
                }
                if (!StringUtils.hasText(name)) {
                    throw new BusinessException("第" + (r + 1) + "行姓名为空");
                }
                for (String required : REQUIRED) {
                    if (!StringUtils.hasText(data.get(required))) {
                        throw new BusinessException("工号" + employeeNo + "，" + required + "为空，请修改后重新上传");
                    }
                }
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    if (TEXT_HEADERS.contains(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                        continue;
                    }
                    try {
                        new BigDecimal(entry.getValue().replace(",", ""));
                    } catch (NumberFormatException ex) {
                        throw new BusinessException("工号" + employeeNo + "，" + entry.getKey() + "格式错误，请修改后重新上传");
                    }
                }
                AttendanceRow rowData = new AttendanceRow();
                rowData.setRowNum(r + 1);
                rowData.setEmployeeNo(employeeNo.trim());
                rowData.setName(name.trim());
                rowData.setData(data);
                rows.add(rowData);
            }
            if (rows.isEmpty()) {
                throw new BusinessException("考勤清单没有数据");
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("考勤清单读取失败: " + ex.getMessage());
        }
    }

    public List<PenaltyRow> parsePenalty(MultipartFile file) {
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            HeaderInfo header = findHeader(sheet, this::canonicalPenalty);
            if (header == null) {
                throw new BusinessException("未找到考勤异常表头，请使用系统模板");
            }
            DataFormatter formatter = new DataFormatter();
            List<PenaltyRow> rows = new ArrayList<>();
            for (int r = header.row + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> data = readRow(row, header.columns, formatter);
                String employeeNo = data.get("工号");
                String name = data.get("姓名");
                if (!StringUtils.hasText(employeeNo) && !StringUtils.hasText(name)) {
                    continue;
                }
                if (!StringUtils.hasText(employeeNo) || !StringUtils.hasText(name)) {
                    throw new BusinessException("第" + (r + 1) + "行工号或姓名为空");
                }
                String importType = data.getOrDefault("导入方式", "新增");
                String code = data.get("编号");
                if ("修改".equals(importType) && !StringUtils.hasText(code)) {
                    throw new BusinessException("工号" + employeeNo + "，导入方式为修改时编号不能为空");
                }
                PenaltyRow rowData = new PenaltyRow();
                rowData.setRowNum(r + 1);
                rowData.setImportType(("修改".equals(importType) || "MODIFY".equalsIgnoreCase(importType)) ? "MODIFY" : "NEW");
                rowData.setCode(code);
                rowData.setEmployeeNo(employeeNo.trim());
                rowData.setName(name.trim());
                rowData.setData(data);
                rows.add(rowData);
            }
            if (rows.isEmpty()) {
                throw new BusinessException("考勤异常清单没有数据");
            }
            return rows;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("考勤异常清单读取失败: " + ex.getMessage());
        }
    }

    public String toJson(Map<String, String> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new BusinessException("数据序列化失败");
        }
    }

    public Map<String, Object> fromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    public byte[] template() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("考勤清单");
            writeHeader(workbook, sheet, HEADERS);
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, i == 0 ? 12 * 256 : 18 * 256);
            }
            return write(workbook);
        } catch (Exception ex) {
            throw new BusinessException("模板生成失败");
        }
    }

    public byte[] penaltyTemplate() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("考勤异常清单");
            writeHeader(workbook, sheet, PENALTY_HEADERS);
            for (int i = 0; i < PENALTY_HEADERS.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            return write(workbook);
        } catch (Exception ex) {
            throw new BusinessException("模板生成失败");
        }
    }

    public byte[] export(List<AttendanceRecord> records) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("考勤清单");
            writeHeader(workbook, sheet, HEADERS);
            int rowIndex = 1;
            for (AttendanceRecord record : records) {
                Row row = sheet.createRow(rowIndex++);
                Map<String, Object> data = fromJson(record.getDataJson());
                for (int i = 0; i < HEADERS.length; i++) {
                    String header = HEADERS[i];
                    String value;
                    if ("员工本人签字确认".equals(header)) {
                        value = "CONFIRMED".equals(record.getStatus()) ? "已确认" : "未确认";
                    } else {
                        Object raw = data.get(header);
                        value = raw == null ? "" : String.valueOf(raw);
                    }
                    row.createCell(i).setCellValue(value);
                }
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, i == 0 ? 12 * 256 : 18 * 256);
            }
            return write(workbook);
        } catch (Exception ex) {
            throw new BusinessException("导出失败");
        }
    }

    private byte[] write(Workbook workbook) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    private void writeHeader(Workbook workbook, Sheet sheet, String[] headers) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private Map<String, String> readRow(Row row, Map<String, Integer> columns, DataFormatter formatter) {
        Map<String, String> data = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : columns.entrySet()) {
            Cell cell = row.getCell(entry.getValue());
            String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
            if (StringUtils.hasText(value)) {
                data.put(entry.getKey(), value);
            }
        }
        return data;
    }

    private HeaderInfo findHeader(Sheet sheet, Function<String, String> canonical) {
        DataFormatter formatter = new DataFormatter();
        int bestRow = -1;
        int bestCount = 0;
        Map<String, Integer> bestColumns = null;
        int limit = Math.min(sheet.getLastRowNum(), 20);
        for (int r = 0; r <= limit; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> columns = new LinkedHashMap<>();
            Map<String, Integer> seen = new HashMap<>();
            int count = 0;
            for (int c = 0; c < row.getLastCellNum(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    continue;
                }
                String header = normalize(formatter.formatCellValue(cell));
                if (!StringUtils.hasText(header)) {
                    continue;
                }
                String key = canonical.apply(header);
                if (key == null) {
                    continue;
                }
                int index = seen.merge(key, 1, Integer::sum);
                if ("下浮绩效点数".equals(key)) {
                    key = index == 1 ? "下浮绩效点数(一级)" : "下浮绩效点数(其他)";
                } else if ("减少表现奖分数".equals(key)) {
                    key = index == 1 ? "减少表现奖分数(一级)" : "减少表现奖分数(其他)";
                } else if (index > 1) {
                    key = key + "(" + index + ")";
                }
                columns.putIfAbsent(key, c);
                count++;
            }
            if (count > bestCount && columns.containsKey("工号") && columns.containsKey("姓名")) {
                bestRow = r;
                bestCount = count;
                bestColumns = columns;
            }
        }
        return bestRow < 0 ? null : new HeaderInfo(bestRow, bestColumns);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "")
                .replace("（", "(")
                .replace("）", ")")
                .replace("*", "");
    }

    private String canonicalAttendance(String header) {
        if (header.contains("工号")) return "工号";
        if (header.contains("姓名")) return "姓名";
        if (header.contains("部门")) return "部门";
        if (header.contains("计资方式")) return "计资方式";
        if (header.contains("工作日缺勤天数")) return "工作日缺勤天数";
        if (header.contains("加点小时数")) return "加点小时数";
        if (header.contains("加班小时数")) return "加班小时数";
        if (header.contains("法定节假日加班小时")) return "法定节假日加班小时";
        if (header.contains("计资类型变化")) return "计资类型变化/计件时应出勤天数";
        if (header.contains("实际出勤天数")) return "实际出勤天数";
        if (header.contains("高温天数")) return "高温天数";
        if (header.contains("病假天数")) return "病假天数";
        if (header.contains("工伤假天数")) return "工伤假天数";
        if (header.contains("福利假天数备注") || "备注".equals(header)
                || (header.contains("福利假天数合计") && header.contains("备注"))) {
            return "福利假天数备注";
        }
        if (header.contains("福利假天数合计")) return "福利假天数合计-FL";
        if (header.contains("入厂之前")) return "入厂之前或离职之后当月工作日天数";
        if (header.contains("与加班冲抵")) return "与加班冲抵后的缺勤天数+FL天数+DA";
        if (header.contains("应出勤天数")) return "应出勤天数";
        if (header.contains("已调休天数")) return "已调休天数";
        if (header.contains("本月年休天数")) return "本月年休天数";
        if (header.contains("加班调休小时数")) return "当月加班调休小时数";
        if (header.contains("旷工天数")) return "旷工天数";
        if (header.contains("迟到10分钟")) return "迟到10分钟含以内";
        if (header.contains("早退10分钟")) return "早退10分钟含以内";
        if (header.contains("迟到10-30")) return "迟到10-30分钟含";
        if (header.contains("早退10-30")) return "早退10-30分钟含";
        if (header.contains("迟到30分钟以上")) return "迟到30分钟以上";
        if (header.contains("早退30分钟以上")) return "早退30分钟以上";
        if (header.contains("个人原因")) return "个人原因打卡/签到异常次数";
        if (header.contains("下浮绩效点数")) return "下浮绩效点数";
        if (header.contains("减少表现奖分数")) return "减少表现奖分数";
        if (header.contains("签字确认")) return "员工本人签字确认";
        return null;
    }

    private String canonicalPenalty(String header) {
        for (String item : PENALTY_HEADERS) {
            if (header.contains(item) || item.contains(header)) {
                return item;
            }
        }
        return null;
    }

    private static class HeaderInfo {
        private final int row;
        private final Map<String, Integer> columns;

        private HeaderInfo(int row, Map<String, Integer> columns) {
            this.row = row;
            this.columns = columns;
        }
    }
}
