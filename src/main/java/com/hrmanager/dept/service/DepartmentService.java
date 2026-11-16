package com.hrmanager.dept.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.common.BusinessException;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final SysDepartmentMapper departmentMapper;
    private final SysUserMapper userMapper;

    public List<SysDepartment> list() {
        return departmentMapper.selectList(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getStatus, "ACTIVE")
                .orderByAsc(SysDepartment::getCode));
    }

    public List<Map<String, Object>> tree() {
        List<SysDepartment> all = list();
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        for (SysDepartment department : all) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", department.getId());
            node.put("code", department.getCode());
            node.put("name", department.getName());
            node.put("parentId", department.getParentId());
            node.put("level", department.getLevel());
            node.put("sort", department.getSort());
            node.put("children", new ArrayList<>());
            nodes.put(department.getId(), node);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (SysDepartment department : all) {
            Map<String, Object> node = nodes.get(department.getId());
            if (department.getParentId() == null) {
                roots.add(node);
            } else {
                Map<String, Object> parent = nodes.get(department.getParentId());
                if (parent == null) {
                    roots.add(node);
                } else {
                    ((List<Map<String, Object>>) parent.get("children")).add(node);
                }
            }
        }
        return roots;
    }

    public SysDepartment get(Long id) {
        SysDepartment department = departmentMapper.selectById(id);
        if (department == null || "DELETED".equals(department.getStatus())) {
            throw new BusinessException("部门不存在");
        }
        return department;
    }

    @Transactional
    public SysDepartment create(SysDepartment body) {
        if (!StringUtils.hasText(body.getName())) {
            throw new BusinessException("部门名称不能为空");
        }
        SysDepartment parent = null;
        int level = 1;
        if (body.getParentId() != null) {
            parent = get(body.getParentId());
            level = parent.getLevel() + 1;
            if (level > 5) {
                throw new BusinessException("最多支持5级部门");
            }
        }
        validateName(body.getParentId(), body.getName(), null);
        SysDepartment department = new SysDepartment();
        department.setName(body.getName());
        department.setParentId(body.getParentId());
        department.setLevel(level);
        department.setCode(nextCode(parent, level));
        department.setStatus("ACTIVE");
        department.setSort(body.getSort() == null ? 0 : body.getSort());
        department.setCreatedAt(LocalDateTime.now());
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.insert(department);
        return department;
    }

    @Transactional
    public SysDepartment update(Long id, SysDepartment body) {
        SysDepartment department = get(id);
        if (StringUtils.hasText(body.getName())) {
            validateName(department.getParentId(), body.getName(), id);
            department.setName(body.getName());
        }
        if (body.getSort() != null) {
            department.setSort(body.getSort());
        }
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);
        return department;
    }

    @Transactional
    public void delete(Long id) {
        SysDepartment department = get(id);
        Long userCount = userMapper.selectCount(Wrappers.<com.hrmanager.user.entity.SysUser>lambdaQuery()
                .eq(com.hrmanager.user.entity.SysUser::getDepartmentId, id)
                .eq(com.hrmanager.user.entity.SysUser::getStatus, "ACTIVE"));
        if (userCount > 0) {
            throw new BusinessException("部门或下属部门中存在有效用户，请调整用户的归属部门后再删除");
        }
        Long childCount = departmentMapper.selectCount(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getParentId, id)
                .eq(SysDepartment::getStatus, "ACTIVE"));
        if (childCount > 0) {
            throw new BusinessException("部门下存在子部门，请先删除子部门");
        }
        department.setStatus("DELETED");
        department.setUpdatedAt(LocalDateTime.now());
        departmentMapper.updateById(department);
    }

    public List<Long> descendantIds(Long departmentId) {
        List<SysDepartment> all = list();
        Map<Long, List<SysDepartment>> children = all.stream()
                .filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(SysDepartment::getParentId));
        List<Long> result = new ArrayList<>();
        result.add(departmentId);
        for (int i = 0; i < result.size(); i++) {
            List<SysDepartment> next = children.get(result.get(i));
            if (next != null) {
                for (SysDepartment department : next) {
                    if (!result.contains(department.getId())) {
                        result.add(department.getId());
                    }
                }
            }
        }
        return result;
    }

    public Map<Long, String> nameMap() {
        return departmentMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysDepartment::getId, SysDepartment::getName, (a, b) -> a));
    }

    private void validateName(Long parentId, String name, Long excludeId) {
        LambdaQueryWrapper<SysDepartment> wrapper = Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getStatus, "ACTIVE")
                .eq(SysDepartment::getName, name);
        if (parentId == null) {
            wrapper.isNull(SysDepartment::getParentId);
        } else {
            wrapper.eq(SysDepartment::getParentId, parentId);
        }
        if (excludeId != null) {
            wrapper.ne(SysDepartment::getId, excludeId);
        }
        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("同级部门名称已存在");
        }
    }

    private String nextCode(SysDepartment parent, int level) {
        if (parent == null) {
            List<SysDepartment> roots = departmentMapper.selectList(Wrappers.<SysDepartment>lambdaQuery()
                    .isNull(SysDepartment::getParentId)
                    .eq(SysDepartment::getStatus, "ACTIVE"));
            int max = 0;
            for (SysDepartment department : roots) {
                max = Math.max(max, Integer.parseInt(department.getCode().substring(0, 2)));
            }
            int next = max + 1;
            if (next > 99) {
                throw new BusinessException("一级部门数量已满");
            }
            return String.format("%02d00000000", next);
        }
        int segment = (level - 1) * 2;
        List<SysDepartment> siblings = departmentMapper.selectList(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getParentId, parent.getId())
                .eq(SysDepartment::getStatus, "ACTIVE"));
        int max = 0;
        for (SysDepartment department : siblings) {
            max = Math.max(max, Integer.parseInt(department.getCode().substring(segment, segment + 2)));
        }
        int next = max + 1;
        if (next > 99) {
            throw new BusinessException("该层级部门数量已满");
        }
        String prefix = parent.getCode().substring(0, segment) + String.format("%02d", next);
        StringBuilder code = new StringBuilder(prefix);
        while (code.length() < 10) {
            code.append('0');
        }
        return code.toString();
    }
}
