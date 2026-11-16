package com.hrmanager.role.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.common.BusinessException;
import com.hrmanager.role.entity.SysRole;
import com.hrmanager.role.mapper.SysRoleMapper;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final SysRoleMapper roleMapper;
    private final SysUserMapper userMapper;

    public List<SysRole> list() {
        return roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
    }

    public SysRole get(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException("角色不存在");
        }
        return role;
    }

    @Transactional
    public SysRole create(SysRole body) {
        if (!StringUtils.hasText(body.getCode()) || !StringUtils.hasText(body.getName())) {
            throw new BusinessException("角色编码和名称不能为空");
        }
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, body.getCode()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在");
        }
        body.setStatus("ACTIVE");
        body.setCreatedAt(LocalDateTime.now());
        if (body.getPermissions() == null) {
            body.setPermissions("");
        }
        roleMapper.insert(body);
        return body;
    }

    @Transactional
    public SysRole update(Long id, SysRole body) {
        SysRole role = get(id);
        if (StringUtils.hasText(body.getName())) {
            role.setName(body.getName());
        }
        if (body.getPermissions() != null) {
            role.setPermissions(body.getPermissions());
        }
        if (StringUtils.hasText(body.getStatus())) {
            role.setStatus(body.getStatus());
        }
        roleMapper.updateById(role);
        return role;
    }

    @Transactional
    public void disable(Long id) {
        SysRole role = get(id);
        role.setStatus("DISABLED");
        roleMapper.updateById(role);
    }

    public Set<String> permissions(Long roleId) {
        if (roleId == null) {
            return Collections.emptySet();
        }
        SysRole role = roleMapper.selectById(roleId);
        if (role == null || !"ACTIVE".equals(role.getStatus()) || !StringUtils.hasText(role.getPermissions())) {
            return Collections.emptySet();
        }
        return Arrays.stream(role.getPermissions().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<String> permissions(SysUser user) {
        return permissions(user == null ? null : user.getRoleId());
    }

    public String roleName(Long roleId) {
        if (roleId == null) {
            return null;
        }
        SysRole role = roleMapper.selectById(roleId);
        return role == null ? null : role.getName();
    }

    public long userCount(Long roleId) {
        return userMapper.selectCount(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getRoleId, roleId));
    }
}
