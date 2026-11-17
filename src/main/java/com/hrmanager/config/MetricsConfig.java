package com.hrmanager.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.attendance.entity.AttendanceBatch;
import com.hrmanager.attendance.entity.AttendanceRecord;
import com.hrmanager.attendance.mapper.AttendanceBatchMapper;
import com.hrmanager.attendance.mapper.AttendanceRecordMapper;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.notice.mapper.SysMessageMapper;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final AttendanceBatchMapper batchMapper;
    private final AttendanceRecordMapper recordMapper;
    private final SysMessageMapper messageMapper;

    @Bean
    public MeterBinder businessGauges() {
        return registry -> {
            Gauge.builder("hr.users.active", this, MetricsConfig::activeUsers)
                    .description("Active users").register(registry);
            Gauge.builder("hr.departments.active", this, MetricsConfig::activeDepartments)
                    .description("Active departments").register(registry);
            Gauge.builder("hr.attendance.batches", this, MetricsConfig::attendanceBatches)
                    .description("Attendance batches").register(registry);
            Gauge.builder("hr.attendance.unconfirmed", this, MetricsConfig::unconfirmedRecords)
                    .description("Attendance records not confirmed").register(registry);
            Gauge.builder("hr.messages.unread", this, MetricsConfig::unreadMessages)
                    .description("Unread in-app messages").register(registry);
        };
    }

    private double activeUsers() {
        return count(() -> userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStatus, "ACTIVE")));
    }

    private double activeDepartments() {
        return count(() -> departmentMapper.selectCount(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getStatus, "ACTIVE")));
    }

    private double attendanceBatches() {
        return count(() -> batchMapper.selectCount(Wrappers.<AttendanceBatch>lambdaQuery()
                .eq(AttendanceBatch::getStatus, "PUBLISHED")));
    }

    private double unconfirmedRecords() {
        return count(() -> recordMapper.selectCount(Wrappers.<AttendanceRecord>lambdaQuery()
                .ne(AttendanceRecord::getStatus, "CONFIRMED")));
    }

    private double unreadMessages() {
        return count(() -> messageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getReadFlag, false)));
    }

    private double count(CountQuery query) {
        try {
            Long value = query.execute();
            return value == null ? 0D : value.doubleValue();
        } catch (RuntimeException ex) {
            log.debug("metric query failed", ex);
            return Double.NaN;
        }
    }

    private interface CountQuery {
        Long execute();
    }
}
