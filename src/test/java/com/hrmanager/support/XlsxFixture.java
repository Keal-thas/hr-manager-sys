package com.hrmanager.support;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

public final class XlsxFixture {

    public static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private XlsxFixture() {
    }

    public static byte[] of(String... lines) {
        Object[][] rows = new Object[lines.length][];
        for (int i = 0; i < lines.length; i++) {
            rows[i] = lines[i] == null ? new Object[0] : lines[i].split(",", -1);
        }
        return ofRows(rows);
    }

    public static byte[] ofRows(Object[]... rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                Object[] cells = rows[r] == null ? new Object[0] : rows[r];
                for (int c = 0; c < cells.length; c++) {
                    Object value = cells[c];
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(c);
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(value));
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to build xlsx fixture", ex);
        }
    }

    public static MockMultipartFile multipart(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, XLSX.toString(), content);
    }
}
