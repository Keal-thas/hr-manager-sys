package com.hrmanager.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hrmanager.attendance.entity.AttendanceRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
}
