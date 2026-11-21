package com.hrmanager.attendance;

import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.support.AttendanceSheetFixture;
import com.hrmanager.support.XlsxFixture;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttendanceRemindApiTests extends AttendanceApiTestSupport {

    @Test
    void attendanceRemindTargetsEveryUnconfirmedEmployee() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        deleteMessagesOfBatch(batch.getId());

        mockMvc.perform(post("/api/admin/attendance/batches/" + batch.getId() + "/remind")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));

        assertEquals(1, messagesOf(userId("10001"), "ATTENDANCE_REMIND", batch.getId()).size());
        assertEquals(1, messagesOf(userId("10002"), "ATTENDANCE_REMIND", batch.getId()).size());
        assertEquals(1, messagesOf(userId("10004"), "ATTENDANCE_REMIND", batch.getId()).size());
        assertTrue(messagesOf(userId("10003"), "ATTENDANCE_REMIND", batch.getId()).isEmpty());
    }

    @Test
    void abnormalRemindTargetsUnreadEmployeesWithPenalties() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        byte[] penalties = XlsxFixture.of(
                AttendanceSheetFixture.penaltyHeader(),
                AttendanceSheetFixture.newPenaltyRow("10001", "管理员", "原因一"),
                AttendanceSheetFixture.newPenaltyRow("10002", "人事专员", "原因二"),
                AttendanceSheetFixture.newPenaltyRow("10004", "李四", "原因三"));
        uploadPenalties(admin, batch.getId(), penalties).andExpect(status().isOk());
        deleteMessagesOfBatch(batch.getId());

        mockMvc.perform(post("/api/admin/attendance/batches/" + batch.getId() + "/remind")
                        .param("type", "ABNORMAL")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        assertEquals(1, messagesOf(userId("10001"), "ATTENDANCE_ABNORMAL", batch.getId()).size());
        assertEquals(1, messagesOf(userId("10004"), "ATTENDANCE_ABNORMAL", batch.getId()).size());
        assertTrue(messagesOf(userId("10002"), "ATTENDANCE_ABNORMAL", batch.getId()).isEmpty());
        assertTrue(messagesOf(userId("10003"), "ATTENDANCE_ABNORMAL", batch.getId()).isEmpty());
    }

    @Test
    void abnormalRemindWithoutPenaltiesSendsNothing() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        deleteMessagesOfBatch(batch.getId());

        mockMvc.perform(post("/api/admin/attendance/batches/" + batch.getId() + "/remind")
                        .param("type", "abnormal")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        assertTrue(messageMapper.selectList(null).stream()
                .noneMatch(message -> "ATTENDANCE_ABNORMAL".equals(message.getType())));
    }

    @Test
    void remindWithAllConfirmedReturnsZero() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);
        deleteMessagesOfBatch(batch.getId());
        List<AttendanceRecord> records = recordsOf(batch.getId());
        for (AttendanceRecord record : records) {
            record.setStatus("CONFIRMED");
            recordMapper.updateById(record);
        }

        mockMvc.perform(post("/api/admin/attendance/batches/" + batch.getId() + "/remind")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void remindRejectsNonLatestBatch() throws Exception {
        MockHttpSession admin = admin();
        YearMonth historical = YearMonth.now().minusMonths(3);
        byte[] attendance = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"));
        upload(admin, historical, "NEW", attendance).andExpect(status().isOk());
        AttendanceBatch batch = batchOf(historical);
        assertNotNull(batch);

        mockMvc.perform(post("/api/admin/attendance/batches/" + batch.getId() + "/remind")
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("仅最新批次支持提醒"));
    }

    @Test
    void remindRejectsUnknownBatch() throws Exception {
        MockHttpSession admin = admin();

        mockMvc.perform(post("/api/admin/attendance/batches/999999/remind")
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤记录不存在"));
    }
}
