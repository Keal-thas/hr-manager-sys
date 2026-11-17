package com.hrmanager.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.attendance.mapper.AttendanceBatchMapper;
import com.hrmanager.attendance.mapper.AttendanceRecordMapper;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.notice.entity.MessageNotice;
import com.hrmanager.notice.entity.NoticeTemplate;
import com.hrmanager.notice.mapper.MessageNoticeMapper;
import com.hrmanager.notice.mapper.NoticeTemplateMapper;
import com.hrmanager.notice.service.MessageService;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements CommandLineRunner {

    private final SysRoleMapper roleMapper;
    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;
    private final AttendanceBatchMapper batchMapper;
    private final AttendanceRecordMapper recordMapper;
    private final MessageNoticeMapper noticeMapper;
    private final NoticeTemplateMapper templateMapper;
    private final PasswordEncoder passwordEncoder;
    private final MessageService messageService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (userMapper.selectCount(null) > 0) {
            return;
        }

        SysRole adminRole = role("ADMIN", "系统管理员",
                "dashboard,my-attendance,my-messages,profile,admin-attendance,admin-notice,admin-template,admin-dept,admin-role,admin-user");
        SysRole hrRole = role("HR", "人事专员",
                "dashboard,my-attendance,my-messages,profile,admin-attendance,admin-notice,admin-template,admin-dept,admin-user");
        SysRole employeeRole = role("EMPLOYEE", "普通员工",
                "dashboard,my-attendance,my-messages,profile");

        SysDepartment it = department("0000000000", "信息科技部", null, 1);
        SysDepartment hr = department("0100000000", "人力资源部", null, 1);
        SysDepartment finance = department("0200000000", "财务部", null, 1);
        SysDepartment rd = department("0001000000", "研发中心", it.getId(), 2);
        SysDepartment hrService = department("0101000000", "人事服务组", hr.getId(), 2);

        SysUser admin = user("10001", "管理员", "Admin1234", it.getId(), adminRole.getId(),
                "13800000001", "admin@example.com");
        SysUser hrUser = user("10002", "人事专员", "Hr123456", hr.getId(), hrRole.getId(),
                "13800000002", "hr@example.com");
        SysUser zhang = user("10003", "张三", "Emp123456", rd.getId(), employeeRole.getId(),
                "13800000003", "zhang@example.com");
        SysUser li = user("10004", "李四", "Emp123456", hrService.getId(), employeeRole.getId(),
                "13800000004", "li@example.com");

        YearMonth ym = YearMonth.now().minusMonths(1);
        AttendanceBatch batch = new AttendanceBatch();
        batch.setBatchYear(ym.getYear());
        batch.setBatchMonth(ym.getMonthValue());
        batch.setStatus("PUBLISHED");
        batch.setUploadMode("NEW");
        batch.setCreatedBy(admin.getId());
        batch.setCreatedAt(LocalDateTime.now());
        batch.setPublishedAt(LocalDateTime.now());
        batch.setLatest(true);
        batchMapper.insert(batch);

        record(batch, admin, it.getName(), "UNREAD", false);
        record(batch, hrUser, hr.getName(), "READ", false);
        record(batch, zhang, rd.getName(), "CONFIRMED", true);
        record(batch, li, hrService.getName(), "UNREAD", false);

        for (SysUser user : Arrays.asList(admin, hrUser, zhang, li)) {
            messageService.send(user.getId(), "ATTENDANCE", "考勤通知",
                    user.getName() + "您好，您" + ym.getMonthValue() + "月份的考勤已生成，请尽快前往员工自助-我的考勤中签收。",
                    batch.getId());
        }

        MessageNotice notice = new MessageNotice();
        notice.setTitle("欢迎使用人事综合管理系统");
        notice.setContent("系统已上线，包含考勤确认、消息通知、部门与用户管理等功能。");
        notice.setTargetType("ALL");
        notice.setPublisherId(admin.getId());
        notice.setStatus("PUBLISHED");
        notice.setCreatedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
        for (SysUser user : Arrays.asList(admin, hrUser, zhang, li)) {
            messageService.send(user.getId(), "NOTICE", notice.getTitle(), notice.getContent(), notice.getId());
        }

        NoticeTemplate template = new NoticeTemplate();
        template.setTitle("新员工入职通知");
        template.setContent("欢迎加入公司！请尽快完成入职资料填写并关注考勤确认。");
        template.setStatus("ACTIVE");
        template.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(template);

        log.info("seed data created: admin=10001/Admin1234, hr=10002/Hr123456, employee=10003/Emp123456");
    }

    private SysRole role(String code, String name, String permissions) {
        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setPermissions(permissions);
        role.setStatus("ACTIVE");
        role.setCreatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        return role;
    }

    private SysDepartment department(String code, String name, Long parentId, int level) {
        SysDepartment department = new SysDepartment();
        department.setCode(code);
        department.setName(name);
        department.setParentId(parentId);
        department.setLevel(level);
        department.setStatus("ACTIVE");
        department.setSort(0);
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.insert(department);
        return department;
    }

    private SysUser user(String employeeNo, String name, String password, Long departmentId, Long roleId,
                         String phone, String email) {
        SysUser user = new SysUser();
        user.setEmployeeNo(employeeNo);
        user.setName(name);
        String encoded = passwordEncoder.encode(password);
        user.setPassword(encoded);
        user.setInitialPassword(encoded);
        user.setDepartmentId(departmentId);
        user.setRoleId(roleId);
        user.setPhone(phone);
        user.setEmail(email);
        user.setStatus("ACTIVE");
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private void record(AttendanceBatch batch, SysUser user, String departmentName, String status, boolean confirmed)
            throws Exception {
        AttendanceRecord record = new AttendanceRecord();
        record.setBatchId(batch.getId());
        record.setEmployeeNo(user.getEmployeeNo());
        record.setUserId(user.getId());
        record.setName(user.getName());
        record.setDepartmentName(departmentName);
        record.setStatus(status);
        record.setDataJson(objectMapper.writeValueAsString(sampleData(user, departmentName)));
        record.setAgree(confirmed);
        if (confirmed) {
            record.setSignatureImage("data:image/png;base64,iVBORw0KGgo=");
            record.setSignatureTime(LocalDateTime.now());
        }
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        recordMapper.insert(record);
    }

    private Map<String, String> sampleData(SysUser user, String departmentName) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("工号", user.getEmployeeNo());
        data.put("姓名", user.getName());
        data.put("部门", departmentName);
        data.put("计资方式", "月薪");
        data.put("工作日缺勤天数", "0");
        data.put("加点小时数", "0");
        data.put("加班小时数", "0");
        data.put("法定节假日加班小时", "0");
        data.put("计资类型变化/计件时应出勤天数", "22");
        data.put("实际出勤天数", "22");
        data.put("高温天数", "0");
        data.put("病假天数", "0");
        data.put("工伤假天数", "0");
        data.put("福利假天数合计-FL", "0");
        data.put("福利假天数备注", "");
        data.put("入厂之前或离职之后当月工作日天数", "0");
        data.put("与加班冲抵后的缺勤天数+FL天数+DA", "0");
        data.put("应出勤天数", "22");
        data.put("已调休天数", "0");
        data.put("本月年休天数", "0");
        data.put("当月加班调休小时数", "0");
        data.put("旷工天数", "0");
        data.put("迟到10分钟含以内", "0");
        data.put("早退10分钟含以内", "0");
        data.put("迟到10-30分钟含", "0");
        data.put("早退10-30分钟含", "0");
        data.put("迟到30分钟以上", "0");
        data.put("早退30分钟以上", "0");
        data.put("个人原因打卡/签到异常次数", "0");
        data.put("下浮绩效点数(一级)", "0");
        data.put("减少表现奖分数(一级)", "0");
        data.put("下浮绩效点数(其他)", "0");
        data.put("减少表现奖分数(其他)", "0");
        data.put("员工本人签字确认", "");
        return data;
    }
}
