package com.hrmanager.attendance;

import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendancePenalty;
import com.hrmanager.support.AttendanceSheetFixture;
import com.hrmanager.support.XlsxFixture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttendancePenaltyApiTests extends AttendanceApiTestSupport {

    private static String code(String employeeNo, int month, int sequence) {
        return employeeNo + String.format("%02d%03d", month, sequence);
    }

    private byte[] sheet(String... rows) {
        String[] lines = new String[rows.length + 1];
        lines[0] = AttendanceSheetFixture.penaltyHeader();
        System.arraycopy(rows, 0, lines, 1, rows.length);
        return XlsxFixture.of(lines);
    }

    @Test
    void newPenaltyCodesUseEmployeeNoMonthAndSequence() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        int month = batch.getBatchMonth();

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因一"),
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因二"),
                AttendanceSheetFixture.newPenaltyRow("10004", "李四", "原因三")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));

        List<AttendancePenalty> penalties = penaltiesOf(batch.getId());
        assertEquals(3, penalties.size());
        assertEquals(code("10003", month, 1), penalties.get(0).getCode());
        assertEquals(code("10003", month, 2), penalties.get(1).getCode());
        assertEquals(code("10004", month, 1), penalties.get(2).getCode());
        assertEquals("NEW", penalties.get(0).getImportType());
        assertEquals("张三", penalties.get(0).getName());
        assertEquals("研发中心", penalties.get(0).getDepartmentName());
        assertTrue(penalties.get(0).getDataJson().contains("原因一"));

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因四")))
                .andExpect(status().isOk());

        penalties = penaltiesOf(batch.getId());
        assertEquals(4, penalties.size());
        assertEquals(code("10003", month, 3), penalties.get(2).getCode());
    }

    @Test
    void modifyPenaltyMatchesCodeAndUpdatesData() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因一"),
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因二")))
                .andExpect(status().isOk());
        String firstCode = penaltiesOf(batch.getId()).get(0).getCode();

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.modifyPenaltyRow(firstCode, "10003", "张三", "原因已修改")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        List<AttendancePenalty> penalties = penaltiesOf(batch.getId());
        assertEquals(2, penalties.size());
        assertTrue(penalties.get(0).getDataJson().contains("原因已修改"));
        assertTrue(penalties.get(1).getDataJson().contains("原因二"));
    }

    @Test
    void modifyPenaltyIsScopedToSelectedYearMonth() throws Exception {
        MockHttpSession admin = admin();
        YearMonth current = YearMonth.now();
        YearMonth sameMonthLastYear = current.minusYears(1);
        AttendanceBatch currentBatch = batchOf(current);
        assertNotNull(currentBatch);

        byte[] attendance = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        upload(admin, sameMonthLastYear, "NEW", attendance).andExpect(status().isOk());
        AttendanceBatch historical = batchOf(sameMonthLastYear);
        assertNotNull(historical);

        uploadPenalties(admin, historical.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "历史原因")))
                .andExpect(status().isOk());
        uploadPenalties(admin, currentBatch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "当前原因")))
                .andExpect(status().isOk());

        String historicalCode = penaltiesOf(historical.getId()).get(0).getCode();
        String currentCode = penaltiesOf(currentBatch.getId()).get(0).getCode();
        assertEquals(historicalCode, currentCode);

        uploadPenalties(admin, currentBatch.getId(), sheet(
                AttendanceSheetFixture.modifyPenaltyRow(currentCode, "10003", "张三", "当前已修改")))
                .andExpect(status().isOk());

        assertTrue(penaltiesOf(currentBatch.getId()).get(0).getDataJson().contains("当前已修改"));
        assertTrue(penaltiesOf(historical.getId()).get(0).getDataJson().contains("历史原因"));
    }

    @Test
    void modifyPenaltyRejectsCodeFromAnotherBatch() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        String foreignCode = code("10003", 1, 999);

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.modifyPenaltyRow(foreignCode, "10003", "张三", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "编号" + foreignCode + "未匹配到对应的处罚明细，请调整后重新导入"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void unknownModifyCodeCancelsWholeImport() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        try {
            uploadPenalties(admin, batch.getId(), sheet(
                    AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因一"),
                    AttendanceSheetFixture.newPenaltyRow("10004", "李四", "原因二")))
                    .andExpect(status().isOk());
            List<AttendancePenalty> before = penaltiesOf(batch.getId());
            String firstCode = before.get(0).getCode();
            String unknownCode = code("10004", batch.getBatchMonth(), 999);

            uploadPenalties(admin, batch.getId(), sheet(
                    AttendanceSheetFixture.modifyPenaltyRow(firstCode, "10003", "张三", "不应保存"),
                    AttendanceSheetFixture.modifyPenaltyRow(unknownCode, "10004", "李四", "不存在")))
                    .andExpect(status().isBadRequest());

            List<AttendancePenalty> after = penaltiesOf(batch.getId());
            assertEquals(2, after.size());
            assertTrue(after.get(0).getDataJson().contains("原因一"));
        } finally {
            penaltyMapper.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<AttendancePenalty>lambdaQuery()
                    .eq(AttendancePenalty::getBatchId, batch.getId())
                    .in(AttendancePenalty::getEmployeeNo, Arrays.asList("10003", "10004")));
        }
    }

    @Test
    void penaltyEmployeeMustBeInSelectedBatch() throws Exception {
        MockHttpSession admin = admin();
        YearMonth historical = YearMonth.now().minusMonths(3);
        byte[] attendance = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        upload(admin, historical, "NEW", attendance).andExpect(status().isOk());
        AttendanceBatch batch = batchOf(historical);
        assertNotNull(batch);

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10004", "李四", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号10004不在该批次考勤中"));
    }

    @Test
    void penaltyEmployeeMustExistAndMatchName() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("00023", "王五", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号00023不存在"));

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "李四", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号10003姓名不匹配"));
    }

    @Test
    void penaltyListFiltersByEmployeeNoAndCode() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因一"),
                AttendanceSheetFixture.newPenaltyRow("10004", "李四", "原因二")))
                .andExpect(status().isOk());
        String code = penaltiesOf(batch.getId()).get(0).getCode();

        mockMvc.perform(get("/api/admin/attendance/batches/" + batch.getId() + "/penalties")
                        .param("keyword", "10004")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].employeeNo").value("10004"));

        mockMvc.perform(get("/api/admin/attendance/batches/" + batch.getId() + "/penalties")
                        .param("keyword", code)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value(code));
    }

    @Test
    void employeeAttendanceDetailIncludesPenalties() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因一")))
                .andExpect(status().isOk());
        String code = penaltiesOf(batch.getId()).get(0).getCode();

        MockHttpSession employee = login("10003", "Emp123456");
        mockMvc.perform(get("/api/my/attendance/" + batch.getId()).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.penalties[0].code").value(code))
                .andExpect(jsonPath("$.data.penalties[0].data.原因").value("原因一"));

        mockMvc.perform(get("/api/admin/attendance/records/"
                        + recordOf(batch.getId(), "10003").getId()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.penalties[0].code").value(code));
    }

    @Test
    void overwriteClearsExistingPenalties() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        uploadPenalties(admin, batch.getId(), sheet(
                AttendanceSheetFixture.newPenaltyRow("10003", "张三", "原因一")))
                .andExpect(status().isOk());
        assertEquals(1, penaltiesOf(batch.getId()).size());

        byte[] attendance = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        upload(admin, YearMonth.now(), "OVERWRITE", attendance).andExpect(status().isOk());

        assertTrue(penaltiesOf(batch.getId()).isEmpty());
    }
}
