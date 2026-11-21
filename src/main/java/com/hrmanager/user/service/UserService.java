package com.hrmanager.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hrmanager.common.BusinessException;
import com.hrmanager.common.PageResult;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysDepartmentMapper departmentMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<Map<String, Object>> page(int page, int size, String keyword, Long departmentId, Long roleId, String status) {
        LambdaQueryWrapper<SysUser> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(q -> q.like(SysUser::getEmployeeNo, keyword)
                    .or().like(SysUser::getName, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        if (departmentId != null) {
            wrapper.eq(SysUser::getDepartmentId, departmentId);
        }
        if (roleId != null) {
            wrapper.eq(SysUser::getRoleId, roleId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt)
                .orderByDesc(SysUser::getId);
        Page<SysUser> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        Map<Long, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));
        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a));
        List<Map<String, Object>> records = result.getRecords().stream()
                .map(u -> view(u, deptNames, roleNames))
                .collect(Collectors.toList());
        return new PageResult<>(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public SysUser get(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    public Map<String, Object> view(Long id) {
        Map<Long, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));
        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a));
        return view(get(id), deptNames, roleNames);
    }

    public Map<String, Object> view(SysUser user) {
        Map<Long, String> deptNames = departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));
        Map<Long, String> roleNames = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getName, (a, b) -> a));
        return view(user, deptNames, roleNames);
    }

    private Map<String, Object> view(SysUser user, Map<Long, String> deptNames, Map<Long, String> roleNames) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("employeeNo", user.getEmployeeNo());
        item.put("name", user.getName());
        item.put("phone", user.getPhone());
        item.put("email", user.getEmail());
        item.put("wecomUserId", user.getWecomUserId());
        item.put("departmentId", user.getDepartmentId());
        item.put("departmentName", deptNames.get(user.getDepartmentId()));
        item.put("roleId", user.getRoleId());
        item.put("roleName", roleNames.get(user.getRoleId()));
        item.put("status", user.getStatus());
        item.put("mustChangePassword", user.getMustChangePassword());
        item.put("createdAt", user.getCreatedAt());
        return item;
    }

    @Transactional
    public SysUser create(SysUser body) {
        validateEmployeeNo(body.getEmployeeNo());
        if (!StringUtils.hasText(body.getName())) {
            throw new BusinessException("姓名不能为空");
        }
        if (body.getDepartmentId() == null) {
            throw new BusinessException("请选择部门");
        }
        if (body.getRoleId() == null) {
            throw new BusinessException("请选择角色");
        }
        if (departmentMapper.selectById(body.getDepartmentId()) == null) {
            throw new BusinessException("部门不存在");
        }
        if (roleMapper.selectById(body.getRoleId()) == null) {
            throw new BusinessException("角色不存在");
        }
        validatePhone(body.getPhone());
        validateEmail(body.getEmail());
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getEmployeeNo, body.getEmployeeNo()));
        if (count > 0) {
            throw new BusinessException("员工工号已存在，请修改后保存");
        }
        String rawPassword = StringUtils.hasText(body.getPassword()) ? body.getPassword() : "bydpcic@123";
        String encoded = passwordEncoder.encode(rawPassword);
        body.setPassword(encoded);
        body.setInitialPassword(encoded);
        body.setStatus("ACTIVE");
        body.setMustChangePassword(true);
        body.setFailedAttempts(0);
        body.setLockedUntil(null);
        body.setCreatedAt(LocalDateTime.now());
        body.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(body);
        return body;
    }

    @Transactional
    public SysUser update(Long id, SysUser body) {
        SysUser user = get(id);
        if (StringUtils.hasText(body.getEmployeeNo()) && !body.getEmployeeNo().equals(user.getEmployeeNo())) {
            throw new BusinessException("员工工号创建后不可修改");
        }
        if (StringUtils.hasText(body.getName())) {
            user.setName(body.getName());
        }
        if (body.getDepartmentId() != null) {
            if (departmentMapper.selectById(body.getDepartmentId()) == null) {
                throw new BusinessException("部门不存在");
            }
            user.setDepartmentId(body.getDepartmentId());
        }
        if (body.getRoleId() != null) {
            if (roleMapper.selectById(body.getRoleId()) == null) {
                throw new BusinessException("角色不存在");
            }
            user.setRoleId(body.getRoleId());
        }
        validatePhone(body.getPhone());
        validateEmail(body.getEmail());
        user.setPhone(body.getPhone());
        user.setEmail(body.getEmail());
        user.setWecomUserId(body.getWecomUserId());
        if (StringUtils.hasText(body.getStatus())) {
            user.setStatus(body.getStatus());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUser user = get(id);
        String raw = StringUtils.hasText(newPassword) ? newPassword : "bydpcic@123";
        String encoded = passwordEncoder.encode(raw);
        user.setPassword(encoded);
        user.setInitialPassword(encoded);
        user.setMustChangePassword(true);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void setStatus(Long id, String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException("状态不合法");
        }
        SysUser user = get(id);
        user.setStatus(status);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    @Transactional
    public void updateProfile(Long id, String phone, String email) {
        validatePhone(phone);
        validateEmail(email);
        SysUser user = get(id);
        user.setPhone(phone);
        user.setEmail(email);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public List<SysUser> listActive() {
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, "ACTIVE"));
    }

    public SysUser findByEmployeeNo(String employeeNo) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("limit 1"));
    }

    private void validatePhone(String phone) {
        if (StringUtils.hasText(phone) && !phone.matches("\\d{11}")) {
            throw new BusinessException("手机号必须为11位数字");
        }
    }

    private void validateEmail(String email) {
        if (StringUtils.hasText(email) && !email.matches("^[^@\\s]+@bydpcic\\.com$")) {
            throw new BusinessException("邮箱必须为@bydpcic.com");
        }
    }

    private void validateEmployeeNo(String employeeNo) {
        if (!StringUtils.hasText(employeeNo) || !employeeNo.matches("\\d+")) {
            throw new BusinessException("工号必须为数字");
        }
    }
}
