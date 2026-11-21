package com.hrmanager.attendance;

import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.support.AttendanceSheetFixture;
import com.hrmanager.support.XlsxFixture;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttendanceStateMachineApiTests extends AttendanceApiTestSupport {

    private AttendanceBatch createHistoricalBatch(YearMonth yearMonth, String... employeeRows) throws Exception {
        String[] lines = new String[employeeRows.length + 1];
        lines[0] = AttendanceSheetFixture.attendanceHeader();
        System.arraycopy(employeeRows, 0, lines, 1, employeeRows.length);
        upload(admin(), yearMonth, "NEW", XlsxFixture.of(lines)).andExpect(status().isOk());
        AttendanceBatch batch = batchOf(yearMonth);
        assertNotNull(batch);
        return batch;
    }

    @Test
    void unreadBecomesReadThenConfirmedAndStaysConfirmed() throws Exception {
        MockHttpSession employee = login("10004", "Emp123456");
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        mockMvc.perform(get("/api/my/attendance/current").session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchId").value(batch.getId()))
                .andExpect(jsonPath("$.data.status").value("UNREAD"));

        mockMvc.perform(get("/api/my/attendance/" + batch.getId()).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        mockMvc.perform(get("/api/my/attendance/" + batch.getId()).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        mockMvc.perform(post("/api/my/attendance/" + batch.getId() + "/confirm")
                        .session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true,\"signatureImage\":\"data:image/png;base64,AAAA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        AttendanceRecord confirmed = recordOf(batch.getId(), "10004");
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(Boolean.TRUE, confirmed.getAgree());
        assertNotNull(confirmed.getSignatureTime());

        mockMvc.perform(get("/api/my/attendance/" + batch.getId()).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void confirmRequiresSignatureAndKeepsUnreadStatus() throws Exception {
        MockHttpSession employee = login("10004", "Emp123456");
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        mockMvc.perform(post("/api/my/attendance/" + batch.getId() + "/confirm")
                        .session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请完成签名"));

        assertEquals("UNREAD", recordOf(batch.getId(), "10004").getStatus());
    }

    @Test
    void confirmRejectsMissingBatchOrRecord() throws Exception {
        MockHttpSession employee = login("10004", "Emp123456");

        mockMvc.perform(post("/api/my/attendance/999999/confirm")
                        .session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true,\"signatureImage\":\"data:image/png;base64,AAAA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤记录不存在"));

        AttendanceBatch historical = createHistoricalBatch(YearMonth.now().minusMonths(3),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));

        mockMvc.perform(post("/api/my/attendance/" + historical.getId() + "/confirm")
                        .session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true,\"signatureImage\":\"data:image/png;base64,AAAA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤明细不存在"));
    }

    @Test
    void confirmTwiceRemainsConfirmed() throws Exception {
        MockHttpSession employee = login("10004", "Emp123456");
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/my/attendance/" + batch.getId() + "/confirm")
                            .session(employee)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"agree\":true,\"signatureImage\":\"data:image/png;base64,AAAA\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }
        assertEquals("CONFIRMED", recordOf(batch.getId(), "10004").getStatus());
    }

    @Test
    void offlineSignatureConfirmsNonLatestRecord() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = createHistoricalBatch(YearMonth.now().minusMonths(3),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        AttendanceRecord record = recordOf(batch.getId(), "10003");
        assertNotNull(record);

        MockMultipartFile file = new MockMultipartFile("file", "sign.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/admin/attendance/records/" + record.getId() + "/offline-signature")
                        .file(file)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        AttendanceRecord confirmed = recordOf(batch.getId(), "10003");
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(Boolean.TRUE, confirmed.getAgree());
        assertTrue(confirmed.getSignatureImage().startsWith("data:image/png;base64,"));
        assertNotNull(confirmed.getSignatureTime());
    }

    @Test
    void offlineSignatureAcceptsPdf() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = createHistoricalBatch(YearMonth.now().minusMonths(3),
                AttendanceSheetFixture.attendanceRow("10004", "李四", "人事服务组"));
        AttendanceRecord record = recordOf(batch.getId(), "10004");

        MockMultipartFile file = new MockMultipartFile("file", "sign.pdf", "application/pdf", new byte[]{1, 2});
        mockMvc.perform(multipart("/api/admin/attendance/records/" + record.getId() + "/offline-signature")
                        .file(file)
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        assertTrue(recordOf(batch.getId(), "10004").getSignatureImage().startsWith("data:application/pdf;base64,"));
    }

    @Test
    void offlineSignatureRejectsLatestBatch() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        AttendanceRecord unread = recordOf(batch.getId(), "10001");
        assertNotNull(unread);
        assertEquals("UNREAD", unread.getStatus());

        MockMultipartFile file = new MockMultipartFile("file", "sign.png", "image/png", new byte[]{1});
        mockMvc.perform(multipart("/api/admin/attendance/records/" + unread.getId() + "/offline-signature")
                        .file(file)
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("最新批次请使用在线签字"));
    }

    @Test
    void offlineSignatureRejectsConfirmedRecord() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        AttendanceRecord confirmed = recordOf(batch.getId(), "10003");
        assertNotNull(confirmed);
        assertEquals("CONFIRMED", confirmed.getStatus());

        MockMultipartFile file = new MockMultipartFile("file", "sign.png", "image/png", new byte[]{1});
        mockMvc.perform(multipart("/api/admin/attendance/records/" + confirmed.getId() + "/offline-signature")
                        .file(file)
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤已确认，无需重复上传"));
    }

    @Test
    void offlineSignatureRejectsEmptyOrUnsupportedFile() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = createHistoricalBatch(YearMonth.now().minusMonths(3),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        AttendanceRecord record = recordOf(batch.getId(), "10003");

        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);
        mockMvc.perform(multipart("/api/admin/attendance/records/" + record.getId() + "/offline-signature")
                        .file(empty)
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请选择签字文件"));

        MockMultipartFile text = new MockMultipartFile("file", "sign.txt", "text/plain", new byte[]{1});
        mockMvc.perform(multipart("/api/admin/attendance/records/" + record.getId() + "/offline-signature")
                        .file(text)
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅支持图片或PDF签字文件"));
        assertEquals("UNREAD", recordOf(batch.getId(), "10003").getStatus());
    }

    @Test
    void myCurrentWithExplicitMonthAndHistory() throws Exception {
        MockHttpSession employee = login("10003", "Emp123456");

        mockMvc.perform(get("/api/my/attendance/current")
                        .param("year", String.valueOf(YearMonth.now().minusYears(2).getYear()))
                        .param("month", "1")
                        .session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(get("/api/my/attendance/history")
                        .param("size", "100")
                        .session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].employeeNo").value("10003"));
    }

    @Test
    void missingRecordAndBatchAreRejected() throws Exception {
        MockHttpSession admin = admin();

        mockMvc.perform(get("/api/admin/attendance/records/999999").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤明细不存在"));

        mockMvc.perform(get("/api/admin/attendance/batches/999999").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤记录不存在"));

        MockHttpSession employee = login("10004", "Emp123456");
        AttendanceBatch historical = createHistoricalBatch(YearMonth.now().minusMonths(3),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        mockMvc.perform(get("/api/my/attendance/" + historical.getId()).session(employee))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤明细不存在"));
    }
}
