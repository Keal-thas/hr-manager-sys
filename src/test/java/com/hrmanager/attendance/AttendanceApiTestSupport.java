package com.hrmanager.attendance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendancePenalty;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.attendance.mapper.AttendanceBatchMapper;
import com.hrmanager.attendance.mapper.AttendancePenaltyMapper;
import com.hrmanager.attendance.mapper.AttendanceRecordMapper;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.notice.mapper.SysMessageMapper;
import com.hrmanager.support.AbstractApiTest;
import com.hrmanager.support.XlsxFixture;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

abstract class AttendanceApiTestSupport extends AbstractApiTest {

    @Autowired
    protected AttendanceBatchMapper batchMapper;

    @Autowired
    protected AttendanceRecordMapper recordMapper;

    @Autowired
    protected AttendancePenaltyMapper penaltyMapper;

    @Autowired
    protected SysMessageMapper messageMapper;

    @Autowired
    protected SysUserMapper userMapper;

    protected MockHttpSession admin() throws Exception {
        return login("10001", "Admin1234");
    }

    protected Long userId(String employeeNo) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("limit 1"));
        return user == null ? null : user.getId();
    }

    protected AttendanceBatch batchOf(YearMonth yearMonth) {
        return batchMapper.selectOne(Wrappers.<AttendanceBatch>lambdaQuery()
                .eq(AttendanceBatch::getBatchYear, yearMonth.getYear())
                .eq(AttendanceBatch::getBatchMonth, yearMonth.getMonthValue())
                .last("limit 1"));
    }

    protected AttendanceRecord recordOf(Long batchId, String employeeNo) {
        return recordMapper.selectOne(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batchId)
                .eq(AttendanceRecord::getEmployeeNo, employeeNo)
                .last("limit 1"));
    }

    protected List<AttendanceRecord> recordsOf(Long batchId) {
        return recordMapper.selectList(Wrappers.<AttendanceRecord>lambdaQuery()
                .eq(AttendanceRecord::getBatchId, batchId)
                .orderByAsc(AttendanceRecord::getEmployeeNo));
    }

    protected List<AttendancePenalty> penaltiesOf(Long batchId) {
        return penaltyMapper.selectList(Wrappers.<AttendancePenalty>lambdaQuery()
                .eq(AttendancePenalty::getBatchId, batchId)
                .orderByAsc(AttendancePenalty::getCode));
    }

    protected ResultActions upload(MockHttpSession session, YearMonth yearMonth, String mode, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart("/api/admin/attendance/upload")
                .file(XlsxFixture.multipart("attendance.xlsx", content))
                .param("year", String.valueOf(yearMonth.getYear()))
                .param("month", String.valueOf(yearMonth.getMonthValue()))
                .param("mode", mode)
                .session(session));
    }

    protected ResultActions uploadPenalties(MockHttpSession session, Long batchId, byte[] content)
            throws Exception {
        return mockMvc.perform(multipart("/api/admin/attendance/batches/" + batchId + "/penalties/upload")
                .file(XlsxFixture.multipart("penalty.xlsx", content))
                .session(session));
    }

    protected void deleteMessagesOfBatch(Long batchId) {
        messageMapper.delete(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getBizId, batchId));
    }

    protected List<SysMessage> messagesOf(Long userId, String type, Long bizId) {
        return messageMapper.selectList(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getType, type)
                .eq(SysMessage::getBizId, bizId));
    }

    protected Map<String, Object> dataOf(AttendanceRecord record) throws Exception {
        return objectMapper.readValue(record.getDataJson(), new TypeReference<Map<String, Object>>() {
        });
    }
}
