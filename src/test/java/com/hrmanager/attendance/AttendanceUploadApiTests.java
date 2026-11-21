package com.hrmanager.attendance;

import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.support.AttendanceSheetFixture;
import com.hrmanager.support.XlsxFixture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttendanceUploadApiTests extends AttendanceApiTestSupport {

    @Test
    void newUploadCreatesBatchWithUnreadRecords() throws Exception {
        MockHttpSession admin = admin();
        YearMonth yearMonth = YearMonth.now().minusMonths(2);

        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心", "3.5", "2"),
                AttendanceSheetFixture.attendanceRow("10004", "李四", "人事服务组"));

        upload(admin, yearMonth, "NEW", content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadMode").value("NEW"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.recordCount").value(2))
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                .andExpect(jsonPath("$.data.confirmedCount").value(0));

        AttendanceBatch batch = batchOf(yearMonth);
        assertNotNull(batch);
        assertFalse(batch.getLatest());
        assertNotNull(batch.getPublishedAt());

        List<AttendanceRecord> records = recordsOf(batch.getId());
        assertEquals(2, records.size());
        AttendanceRecord zhang = recordOf(batch.getId(), "10003");
        assertEquals("UNREAD", zhang.getStatus());
        assertEquals(Boolean.FALSE, zhang.getAgree());
        assertEquals("研发中心", zhang.getDepartmentName());
        assertEquals("3.5", dataOf(zhang).get("工作日缺勤天数"));
        assertEquals("2", dataOf(zhang).get("加班小时数"));

        List<SysMessage> zhangMessages = messagesOf(userId("10003"), "ATTENDANCE", batch.getId());
        assertEquals(1, zhangMessages.size());
        assertTrue(zhangMessages.get(0).getContent().contains("已生成"));
        assertEquals(1, messagesOf(userId("10004"), "ATTENDANCE", batch.getId()).size());
    }

    @Test
    void newUploadRejectsExistingMonth() throws Exception {
        MockHttpSession admin = admin();
        YearMonth current = YearMonth.now();
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));

        upload(admin, current, "NEW", content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该月份考勤已存在，请使用覆盖上传或增量上传"));
    }

    @Test
    void newUploadRejectsFutureMonth() throws Exception {
        MockHttpSession admin = admin();

        upload(admin, YearMonth.now().plusMonths(1), "NEW", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("新增考勤不能晚于当前时间，请修改"));
    }

    @Test
    void newUploadRejectsInvalidYear() throws Exception {
        MockHttpSession admin = admin();

        upload(admin, YearMonth.of(1999, 1), "NEW", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤年份不合法"));
    }

    @Test
    void newUploadRejectsInvalidMode() throws Exception {
        MockHttpSession admin = admin();

        upload(admin, YearMonth.now().minusMonths(2), "MERGE", new byte[0])
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("上传方式不合法"));
    }

    @Test
    void newUploadRejectsDuplicateEmployeeInSheet() throws Exception {
        MockHttpSession admin = admin();
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));

        upload(admin, YearMonth.now().minusMonths(2), "NEW", content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("清单中存在重复人员，请修改后重新上传"));
    }

    @Test
    void newUploadRejectsUnknownEmployeeAndPreservesLeadingZeroInput() throws Exception {
        MockHttpSession admin = admin();
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("00023", "王五", "研发中心"));

        upload(admin, YearMonth.now().minusMonths(2), "NEW", content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号00023不存在，请修改后重新上传"));
    }

    @Test
    void newUploadRejectsNameMismatch() throws Exception {
        MockHttpSession admin = admin();
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "李四", "研发中心"));

        upload(admin, YearMonth.now().minusMonths(2), "NEW", content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号10003姓名不匹配，请修改后重新上传"));
    }

    @Test
    void invalidRowCancelsWholeNewBatch() throws Exception {
        MockHttpSession admin = admin();
        YearMonth yearMonth = YearMonth.now().minusMonths(2);
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"),
                AttendanceSheetFixture.attendanceRow("00023", "王五", "研发中心"));

        upload(admin, yearMonth, "NEW", content)
                .andExpect(status().isBadRequest());

        assertNull(batchOf(yearMonth));
    }

    @Test
    void overwriteReplacesAllRecordsOfLatestBatch() throws Exception {
        MockHttpSession admin = admin();
        YearMonth current = YearMonth.now();
        AttendanceBatch batch = batchOf(current);
        assertNotNull(batch);
        deleteMessagesOfBatch(batch.getId());

        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心", "4", "2.5"),
                AttendanceSheetFixture.attendanceRow("10004", "李四", "人事服务组"));

        upload(admin, current, "OVERWRITE", content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadMode").value("OVERWRITE"))
                .andExpect(jsonPath("$.data.recordCount").value(2));

        assertEquals(2, recordsOf(batch.getId()).size());
        assertNull(recordOf(batch.getId(), "10001"));
        AttendanceRecord zhang = recordOf(batch.getId(), "10003");
        assertEquals("UNREAD", zhang.getStatus());
        assertEquals("4", dataOf(zhang).get("工作日缺勤天数"));
        assertEquals("2.5", dataOf(zhang).get("加班小时数"));
        assertTrue(batchOf(current).getLatest());

        List<SysMessage> zhangMessages = messagesOf(userId("10003"), "ATTENDANCE", batch.getId());
        assertEquals(1, zhangMessages.size());
        assertTrue(zhangMessages.get(0).getContent().contains("已调整"));
    }

    @Test
    void overwriteRejectsNonLatestMonth() throws Exception {
        MockHttpSession admin = admin();
        YearMonth historical = YearMonth.now().minusMonths(3);
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));

        upload(admin, historical, "NEW", content).andExpect(status().isOk());

        upload(admin, historical, "OVERWRITE", content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅最新批次支持覆盖上传"));
    }

    @Test
    void overwriteRejectsMissingBatch() throws Exception {
        MockHttpSession admin = admin();
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));

        upload(admin, YearMonth.now().minusMonths(4), "OVERWRITE", content)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该月份考勤不存在，请先新增考勤"));
    }

    @Test
    void incrementalAddsMissingEmployeeAndUpdatesExisting() throws Exception {
        MockHttpSession admin = admin();
        YearMonth current = YearMonth.now();
        AttendanceBatch batch = batchOf(current);
        assertNotNull(batch);
        AttendanceRecord removed = recordOf(batch.getId(), "10003");
        assertNotNull(removed);
        recordMapper.deleteById(removed.getId());
        deleteMessagesOfBatch(batch.getId());

        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"),
                AttendanceSheetFixture.attendanceRow("10004", "李四", "人事服务组", "7", "0"));

        upload(admin, current, "INCREMENTAL", content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadMode").value("INCREMENTAL"))
                .andExpect(jsonPath("$.data.recordCount").value(4));

        assertEquals(4, recordsOf(batch.getId()).size());
        AttendanceRecord added = recordOf(batch.getId(), "10003");
        assertEquals("UNREAD", added.getStatus());
        AttendanceRecord updated = recordOf(batch.getId(), "10004");
        assertEquals("7", dataOf(updated).get("工作日缺勤天数"));

        List<SysMessage> addedMessages = messagesOf(userId("10003"), "ATTENDANCE", batch.getId());
        assertEquals(1, addedMessages.size());
        assertTrue(addedMessages.get(0).getContent().contains("已生成"));
        List<SysMessage> updatedMessages = messagesOf(userId("10004"), "ATTENDANCE", batch.getId());
        assertEquals(1, updatedMessages.size());
        assertTrue(updatedMessages.get(0).getContent().contains("已调整"));
    }

    @Test
    void incrementalOnHistoricalMonthDoesNotNotify() throws Exception {
        MockHttpSession admin = admin();
        YearMonth historical = YearMonth.now().minusMonths(5);
        byte[] first = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));

        upload(admin, historical, "NEW", first).andExpect(status().isOk());
        AttendanceBatch batch = batchOf(historical);
        assertNotNull(batch);
        deleteMessagesOfBatch(batch.getId());

        byte[] second = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心", "1", "0"),
                AttendanceSheetFixture.attendanceRow("10004", "李四", "人事服务组"));

        upload(admin, historical, "INCREMENTAL", second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordCount").value(2));

        assertEquals(2, recordsOf(batch.getId()).size());
        assertTrue(messagesOf(userId("10003"), "ATTENDANCE", batch.getId()).isEmpty());
        assertTrue(messagesOf(userId("10004"), "ATTENDANCE", batch.getId()).isEmpty());
    }
}
