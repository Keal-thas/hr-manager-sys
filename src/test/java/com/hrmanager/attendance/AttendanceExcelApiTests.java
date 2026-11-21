package com.hrmanager.attendance;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.attendance.service.AttendanceExcelService;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.support.AttendanceSheetFixture;
import com.hrmanager.support.XlsxFixture;
import com.hrmanager.user.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttendanceExcelApiTests extends AttendanceApiTestSupport {

    @Autowired
    private SysRoleMapper roleMapper;

    private Object[] attendanceValues(String employeeNo, String name) {
        return AttendanceSheetFixture.attendanceValues(employeeNo, name, "研发中心", "0", "0");
    }

    private byte[] attendanceSheet(Object[]... rows) {
        Object[][] all = new Object[rows.length + 1][];
        all[0] = AttendanceExcelService.HEADERS;
        System.arraycopy(rows, 0, all, 1, rows.length);
        return XlsxFixture.ofRows(all);
    }

    private byte[] penaltySheet(String[]... rows) {
        Object[][] all = new Object[rows.length + 1][];
        all[0] = AttendanceSheetFixture.penaltyHeaderValues();
        System.arraycopy(rows, 0, all, 1, rows.length);
        return XlsxFixture.ofRows(all);
    }

    private void createEmployee(String employeeNo, String name) throws Exception {
        SysUser seed = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, "10003")
                .last("limit 1"));
        SysRole role = roleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getCode, "EMPLOYEE")
                .last("limit 1"));
        mockMvc.perform(post("/api/admin/users")
                        .session(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNo\":\"" + employeeNo + "\",\"name\":\"" + name
                                + "\",\"departmentId\":" + seed.getDepartmentId()
                                + ",\"roleId\":" + role.getId() + "}"))
                .andExpect(status().isOk());
    }

    @Test
    void numericAndTextCellsMixInOneSheet() throws Exception {
        MockHttpSession admin = admin();
        YearMonth yearMonth = YearMonth.now().minusMonths(2);
        Object[] values = attendanceValues("10003", "张三");
        values[4] = Integer.valueOf(1);
        values[6] = Double.valueOf(2.5);
        values[10] = Double.valueOf(0.25);

        upload(admin, yearMonth, "NEW", attendanceSheet(values)).andExpect(status().isOk());

        AttendanceBatch batch = batchOf(yearMonth);
        assertNotNull(batch);
        AttendanceRecord record = recordOf(batch.getId(), "10003");
        Map<String, Object> data = dataOf(record);
        assertEquals("1", data.get("工作日缺勤天数"));
        assertEquals("2.5", data.get("加班小时数"));
        assertEquals("0.25", data.get("高温天数"));
        assertEquals("月薪", data.get("计资方式"));
    }

    @Test
    void leadingZeroEmployeeNoRequiresTextCell() throws Exception {
        MockHttpSession admin = admin();
        createEmployee("00023", "王五");
        YearMonth first = YearMonth.now().minusMonths(2);
        YearMonth second = YearMonth.now().minusMonths(4);

        upload(admin, first, "NEW", attendanceSheet(attendanceValues("00023", "王五")))
                .andExpect(status().isOk());
        assertNotNull(recordOf(batchOf(first).getId(), "00023"));

        Object[] numeric = attendanceValues("23", "王五");
        numeric[0] = Integer.valueOf(23);
        upload(admin, second, "NEW", attendanceSheet(numeric))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号23不存在，请修改后重新上传"));
    }

    @Test
    void blankRowsAreIgnored() throws Exception {
        MockHttpSession admin = admin();
        YearMonth yearMonth = YearMonth.now().minusMonths(2);
        byte[] content = XlsxFixture.of(
                AttendanceSheetFixture.attendanceHeader(),
                AttendanceSheetFixture.attendanceRow("10003", "张三", "研发中心"),
                "",
                AttendanceSheetFixture.attendanceRow("10004", "李四", "人事服务组"));

        upload(admin, yearMonth, "NEW", content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordCount").value(2));
        assertEquals(2, recordsOf(batchOf(yearMonth).getId()).size());
    }

    @Test
    void missingHeaderIsRejected() throws Exception {
        MockHttpSession admin = admin();

        upload(admin, YearMonth.now().minusMonths(2), "NEW", XlsxFixture.of("foo,bar", "1,2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未找到考勤表头，请使用系统模板"));
    }

    @Test
    void missingRequiredColumnIsRejected() throws Exception {
        MockHttpSession admin = admin();

        upload(admin, YearMonth.now().minusMonths(2), "NEW", XlsxFixture.of("工号,姓名", "10003,张三"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("工号10003")))
                .andExpect(jsonPath("$.message").value(containsString("为空")));
    }

    @Test
    void nonNumericAndTooManyDecimalPlacesAreRejected() throws Exception {
        MockHttpSession admin = admin();
        YearMonth yearMonth = YearMonth.now().minusMonths(2);

        Object[] nonNumeric = attendanceValues("10003", "张三");
        nonNumeric[6] = "abc";
        upload(admin, yearMonth, "NEW", attendanceSheet(nonNumeric))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("加班小时数格式错误")));

        Object[] tooManyDecimals = attendanceValues("10003", "张三");
        tooManyDecimals[6] = "1.1234567";
        upload(admin, yearMonth, "NEW", attendanceSheet(tooManyDecimals))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("最多支持小数点后6位")));
    }

    @Test
    void overlongTextValuesAreRejected() throws Exception {
        MockHttpSession admin = admin();
        Object[] values = attendanceValues("10003", "张三");
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            tooLong.append('a');
        }
        values[3] = tooLong.toString();

        upload(admin, YearMonth.now().minusMonths(2), "NEW", attendanceSheet(values))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("计资方式最多支持100个字符")));
    }

    @Test
    void emptyAttendanceSheetIsRejected() throws Exception {
        MockHttpSession admin = admin();

        upload(admin, YearMonth.now().minusMonths(2), "NEW",
                XlsxFixture.of(AttendanceSheetFixture.attendanceHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤清单没有数据"));
    }

    @Test
    void penaltyImportTypeAndCodeRules() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        uploadPenalties(admin, batch.getId(), penaltySheet(
                AttendanceSheetFixture.penaltyValues("", "", "10003", "张三", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号10003，导入方式为空，请修改后重新上传"));

        uploadPenalties(admin, batch.getId(), penaltySheet(
                AttendanceSheetFixture.penaltyValues("新增", "X", "10003", "张三", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号10003，导入方式为新增时编号必须为空"));

        uploadPenalties(admin, batch.getId(), penaltySheet(
                AttendanceSheetFixture.penaltyValues("修改", "", "10003", "张三", "原因")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工号10003，导入方式为修改时编号不能为空"));
    }

    @Test
    void penaltyDateAndMonthFormatRules() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        String[] badDate = AttendanceSheetFixture.penaltyValues("新增", "", "10003", "张三", "原因");
        badDate[5] = "2026/01/05";
        uploadPenalties(admin, batch.getId(), penaltySheet(badDate))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("开单日期格式应为yyyy-MM-dd")));

        String[] badMonth = AttendanceSheetFixture.penaltyValues("新增", "", "10003", "张三", "原因");
        badMonth[6] = "2026-13";
        uploadPenalties(admin, batch.getId(), penaltySheet(badMonth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("执行月份格式应为YYYY-MM")));
    }

    @Test
    void penaltyLengthAndDecimalRules() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 51; i++) {
            tooLong.append('a');
        }
        String[] longKind = AttendanceSheetFixture.penaltyValues("新增", "", "10003", "张三", "原因");
        longKind[7] = tooLong.toString();
        uploadPenalties(admin, batch.getId(), penaltySheet(longKind))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("惩罚种类最多支持50个字符")));

        String[] badDecimal = AttendanceSheetFixture.penaltyValues("新增", "", "10003", "张三", "原因");
        badDecimal[15] = "abc";
        uploadPenalties(admin, batch.getId(), penaltySheet(badDecimal))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("无绩效奖或表现奖月数格式错误")));
    }

    @Test
    void penaltyEmptySheetAndMissingHeaderAreRejected() throws Exception {
        MockHttpSession admin = admin();
        AttendanceBatch batch = batchOf(YearMonth.now());
        assertNotNull(batch);

        uploadPenalties(admin, batch.getId(), XlsxFixture.of(AttendanceSheetFixture.penaltyHeader()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤异常清单没有数据"));

        uploadPenalties(admin, batch.getId(), XlsxFixture.of("foo,bar", "1,2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("未找到考勤异常表头，请使用系统模板"));
    }
}
