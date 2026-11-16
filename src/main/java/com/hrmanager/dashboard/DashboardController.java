package com.hrmanager.dashboard;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.attendance.service.AttendanceService;
import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.LoginUser;
import com.hrmanager.common.SessionUtil;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.notice.mapper.MessageNoticeMapper;
import com.hrmanager.notice.service.MessageService;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final SysRoleMapper roleMapper;
    private final MessageNoticeMapper noticeMapper;
    private final MessageService messageService;
    private final AttendanceService attendanceService;

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        LoginUser loginUser = SessionUtil.current(request);
        Map<String, Object> result = new HashMap<>();
        result.put("userCount", userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, "ACTIVE")));
        result.put("departmentCount", departmentMapper.selectCount(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getStatus, "ACTIVE")));
        result.put("roleCount", roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getStatus, "ACTIVE")));
        result.put("noticeCount", noticeMapper.selectCount(null));
        result.put("unreadCount", messageService.unreadCount(loginUser.getId()));
        result.put("latestBatch", attendanceService.latestBatch() == null ? null
                : attendanceService.batchView(attendanceService.latestBatch()));
        result.put("myAttendance", attendanceService.myCurrent(loginUser.getId()));
        return ApiResponse.ok(result);
    }
}
