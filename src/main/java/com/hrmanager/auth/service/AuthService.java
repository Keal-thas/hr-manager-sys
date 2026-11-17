package com.hrmanager.auth.service;

import com.hrmanager.common.BusinessException;
import com.hrmanager.common.BusinessMetrics;
import com.hrmanager.common.LoginUser;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.role.service.RoleService;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDepartmentMapper departmentMapper;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final BusinessMetrics metrics;

    @Transactional
    public Map<String, Object> login(String employeeNo, String password) {
        if (!StringUtils.hasText(employeeNo) || !StringUtils.hasText(password)) {
            metrics.login("invalid");
            throw new BusinessException("请输入工号和密码");
        }
        SysUser user = userMapper.selectOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, employeeNo.trim())
                .last("limit 1"));
        if (user == null) {
            metrics.login("unknown_user");
            throw new BusinessException("工号不存在，请重新输入");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            metrics.login("disabled");
            throw new BusinessException("账号已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            metrics.login("locked");
            throw new BusinessException("密码错误5次，请稍后再试");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            int attempts = user.getFailedAttempts() == null ? 0 : user.getFailedAttempts();
            attempts++;
            if (attempts >= 5) {
                user.setFailedAttempts(0);
                user.setLockedUntil(now.plusMinutes(15));
                userMapper.updateById(user);
                metrics.login("locked");
                throw new BusinessException("密码错误5次，请稍后再试");
            }
            user.setFailedAttempts(attempts);
            userMapper.updateById(user);
            metrics.login("bad_password");
            throw new BusinessException("密码错误，请重新输入");
        }
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userMapper.updateById(user);
        LoginUser loginUser = buildLoginUser(user);
        Map<String, Object> result = new HashMap<>();
        result.put("user", view(user));
        result.put("roleCode", loginUser.getRoleCode());
        result.put("permissions", loginUser.getPermissions());
        result.put("mustChangePassword", loginUser.isMustChangePassword());
        result.put("menus", com.hrmanager.common.Menus.forPermissions(loginUser.getPermissions()));
        metrics.login("success");
        return result;
    }

    public LoginUser buildLoginUser(SysUser user) {
        Set<String> permissions = roleService.permissions(user);
        SysRole role = user.getRoleId() == null ? null : roleMapper.selectById(user.getRoleId());
        return new LoginUser(user.getId(), user.getEmployeeNo(), user.getName(),
                user.getDepartmentId(), role == null ? null : role.getCode(),
                permissions, Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    public Map<String, Object> me(LoginUser loginUser) {
        SysUser user = userMapper.selectById(loginUser.getId());
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("user", view(user));
        result.put("permissions", roleService.permissions(user));
        result.put("mustChangePassword", Boolean.TRUE.equals(user.getMustChangePassword()));
        result.put("menus", com.hrmanager.common.Menus.forPermissions(roleService.permissions(user)));
        return result;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        try {
            doChangePassword(userId, oldPassword, newPassword);
            metrics.passwordChange("success");
        } catch (BusinessException ex) {
            metrics.passwordChange("failure");
            throw ex;
        }
    }

    private void doChangePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码错误，请重新录入");
        }
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同，请重新录入");
        }
        if (user.getInitialPassword() != null && passwordEncoder.matches(newPassword, user.getInitialPassword())) {
            throw new BusinessException("新密码不能与初始密码相同，请重新录入");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new BusinessException("新密码不能小于8位，请重新输入");
        }
        if (password.length() > 50) {
            throw new BusinessException("新密码不能超过50位");
        }
        if (password.matches(".*[\\u4e00-\\u9fa5].*")) {
            throw new BusinessException("密码不能包含汉字");
        }
        if (password.matches(".*\\s.*")) {
            throw new BusinessException("密码不能包含空格");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new BusinessException("新密码缺少大写字母，请重新输入");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new BusinessException("新密码缺少小写字母，请重新输入");
        }
        if (!password.matches(".*\\d.*")) {
            throw new BusinessException("新密码缺少数字，请重新输入");
        }
    }

    public Map<String, Object> view(SysUser user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("employeeNo", user.getEmployeeNo());
        item.put("name", user.getName());
        item.put("phone", user.getPhone());
        item.put("email", user.getEmail());
        item.put("departmentId", user.getDepartmentId());
        SysDepartment department = user.getDepartmentId() == null ? null : departmentMapper.selectById(user.getDepartmentId());
        item.put("departmentName", department == null ? null : department.getName());
        item.put("roleId", user.getRoleId());
        item.put("roleName", roleService.roleName(user.getRoleId()));
        item.put("status", user.getStatus());
        item.put("mustChangePassword", user.getMustChangePassword());
        return item;
    }
}
