package com.hrmanager.notice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hrmanager.common.BusinessException;
import com.hrmanager.common.BusinessMetrics;
import com.hrmanager.common.PageResult;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.notice.mapper.SysMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final SysMessageMapper messageMapper;
    private final BusinessMetrics metrics;

    @Transactional
    public void send(Long userId, String type, String title, String content, Long bizId) {
        if (userId == null) {
            return;
        }
        SysMessage message = new SysMessage();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setBizId(bizId);
        message.setReadFlag(false);
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        metrics.messageSent(type);
    }

    public void sendBatch(List<Long> userIds, String type, String title, String content, Long bizId) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            send(userId, type, title, content, bizId);
        }
    }

    public PageResult<SysMessage> page(Long userId, int page, int size, String type, Boolean unreadOnly) {
        LambdaQueryWrapper<SysMessage> wrapper = Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .orderByDesc(SysMessage::getCreatedAt);
        if (StringUtils.hasText(type)) {
            wrapper.eq(SysMessage::getType, type);
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(SysMessage::getReadFlag, false);
        }
        Page<SysMessage> result = messageMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    public SysMessage get(Long id, Long userId) {
        SysMessage message = messageMapper.selectById(id);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new BusinessException("消息不存在");
        }
        return message;
    }

    public long unreadCount(Long userId) {
        return messageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getReadFlag, false));
    }

    @Transactional
    public void read(Long id, Long userId) {
        SysMessage message = messageMapper.selectById(id);
        if (message == null || !userId.equals(message.getUserId())) {
            throw new BusinessException("消息不存在");
        }
        if (!Boolean.TRUE.equals(message.getReadFlag())) {
            message.setReadFlag(true);
            messageMapper.updateById(message);
        }
    }

    @Transactional
    public void readAll(Long userId) {
        messageMapper.update(null, Wrappers.<SysMessage>lambdaUpdate()
                .set(SysMessage::getReadFlag, true)
                .eq(SysMessage::getUserId, userId)
                .eq(SysMessage::getReadFlag, false));
    }
}
