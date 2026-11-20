package com.hrmanager.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hrmanager.common.BusinessException;
import com.hrmanager.common.PageResult;
import com.hrmanager.common.Req;
import com.hrmanager.dept.service.DepartmentService;
import com.hrmanager.notice.entity.MessageNotice;
import com.hrmanager.notice.entity.NoticeTemplate;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.notice.mapper.MessageNoticeMapper;
import com.hrmanager.notice.mapper.NoticeTemplateMapper;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final MessageNoticeMapper noticeMapper;
    private final NoticeTemplateMapper templateMapper;
    private final SysUserMapper userMapper;
    private final DepartmentService departmentService;
    private final MessageService messageService;

    @Transactional
    public Map<String, Object> publish(Map<String, Object> body, Long publisherId) {
        String title = Req.str(body, "title");
        String content = Req.str(body, "content");
        String targetType = Req.str(body, "targetType", "ALL");
        String targetIds = Req.str(body, "targetIds");
        if (!StringUtils.hasText(title)) {
            throw new BusinessException("通知标题不能为空");
        }
        if (title.length() > 100) {
            throw new BusinessException("通知标题不能超过100个字符");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("通知内容不能为空");
        }
        if (content.length() > 10000) {
            throw new BusinessException("通知内容不能超过10000个字符");
        }
        List<SysUser> users = resolveUsers(targetType, targetIds);
        if (users.isEmpty()) {
            throw new BusinessException("通知目标为空");
        }
        MessageNotice notice = new MessageNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setTargetType(targetType);
        notice.setTargetIds(targetIds);
        notice.setPublisherId(publisherId);
        notice.setStatus("PUBLISHED");
        notice.setCreatedAt(LocalDateTime.now());
        noticeMapper.insert(notice);
        for (SysUser user : users) {
            messageService.send(user.getId(), "NOTICE", title, content, notice.getId());
        }
        if (Req.bool(body, "saveAsTemplate")) {
            NoticeTemplate template = new NoticeTemplate();
            template.setTitle(title);
            template.setContent(content);
            template.setStatus("ACTIVE");
            template.setCreatedAt(LocalDateTime.now());
            templateMapper.insert(template);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("id", notice.getId());
        result.put("targetCount", users.size());
        return result;
    }

    private List<SysUser> resolveUsers(String targetType, String targetIds) {
        if ("ALL".equalsIgnoreCase(targetType)) {
            return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getStatus, "ACTIVE"));
        }
        List<Long> ids = parseIds(targetIds);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        if ("USER".equalsIgnoreCase(targetType)) {
            return userMapper.selectBatchIds(ids).stream()
                    .filter(u -> "ACTIVE".equals(u.getStatus()))
                    .collect(Collectors.toList());
        }
        if ("DEPT".equalsIgnoreCase(targetType)) {
            List<Long> departmentIds = new ArrayList<>();
            for (Long id : ids) {
                departmentIds.addAll(departmentService.descendantIds(id));
            }
            if (departmentIds.isEmpty()) {
                return Collections.emptyList();
            }
            return userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                    .in(SysUser::getDepartmentId, departmentIds)
                    .eq(SysUser::getStatus, "ACTIVE"));
        }
        throw new BusinessException("通知目标类型不合法");
    }

    public PageResult<MessageNotice> page(int page, int size) {
        Page<MessageNotice> result = noticeMapper.selectPage(new Page<>(page, size),
                Wrappers.<MessageNotice>lambdaQuery().orderByDesc(MessageNotice::getCreatedAt)
                        .orderByDesc(MessageNotice::getId));
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public MessageNotice get(Long id) {
        MessageNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException("通知不存在");
        }
        return notice;
    }

    public List<NoticeTemplate> templates(String status, String keyword) {
        LambdaQueryWrapper<NoticeTemplate> wrapper = Wrappers.<NoticeTemplate>lambdaQuery()
                .orderByDesc(NoticeTemplate::getCreatedAt)
                .orderByDesc(NoticeTemplate::getId);
        if (!StringUtils.hasText(status)) {
            wrapper.eq(NoticeTemplate::getStatus, "ACTIVE");
        } else if (!"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(NoticeTemplate::getStatus, status.toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(NoticeTemplate::getTitle, keyword.trim());
        }
        return templateMapper.selectList(wrapper);
    }

    @Transactional
    public NoticeTemplate createTemplate(NoticeTemplate body) {
        validateTemplate(body);
        body.setStatus("ACTIVE");
        body.setCreatedAt(LocalDateTime.now());
        templateMapper.insert(body);
        return body;
    }

    @Transactional
    public NoticeTemplate updateTemplate(Long id, NoticeTemplate body) {
        NoticeTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (StringUtils.hasText(body.getTitle()) && body.getTitle().length() > 200) {
            throw new BusinessException("模板标题不能超过200个字符");
        }
        if (StringUtils.hasText(body.getContent()) && body.getContent().length() > 10000) {
            throw new BusinessException("模板内容不能超过10000个字符");
        }
        if (StringUtils.hasText(body.getTitle())) {
            template.setTitle(body.getTitle());
        }
        if (StringUtils.hasText(body.getContent())) {
            template.setContent(body.getContent());
        }
        templateMapper.updateById(template);
        return template;
    }

    @Transactional
    public void disableTemplate(Long id) {
        NoticeTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setStatus("DISABLED");
        templateMapper.updateById(template);
    }

    @Transactional
    public void restoreTemplate(Long id) {
        NoticeTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        template.setStatus("ACTIVE");
        templateMapper.updateById(template);
    }

    private void validateTemplate(NoticeTemplate body) {
        if (!StringUtils.hasText(body.getTitle()) || !StringUtils.hasText(body.getContent())) {
            throw new BusinessException("模板标题和内容不能为空");
        }
        if (body.getTitle().length() > 200) {
            throw new BusinessException("模板标题不能超过200个字符");
        }
        if (body.getContent().length() > 10000) {
            throw new BusinessException("模板内容不能超过10000个字符");
        }
    }

    private List<Long> parseIds(String value) {
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }
}
