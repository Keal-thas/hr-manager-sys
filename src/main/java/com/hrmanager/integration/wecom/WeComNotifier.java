package com.hrmanager.integration.wecom;

import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeComNotifier {

    private final WeComProperties properties;
    private final WeComClient client;
    private final SysUserMapper userMapper;
    private final WeComMessageLogMapper logMapper;

    public boolean isEnabled() {
        return properties.isReady();
    }

    @Async("weComExecutor")
    public void send(Long userId, String scene, String title, String content) {
        if (!properties.isReady()) {
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        String wecomUserId = resolveUserId(user);
        WeComMessageLog entry = new WeComMessageLog();
        entry.setUserId(userId);
        entry.setEmployeeNo(user.getEmployeeNo());
        entry.setWecomUserId(wecomUserId);
        entry.setScene(scene);
        entry.setTitle(title);
        entry.setContent(truncate(content));
        entry.setCreatedAt(LocalDateTime.now());

        if (!StringUtils.hasText(wecomUserId)) {
            entry.setStatus(WeComMessageLog.STATUS_SKIPPED);
            entry.setErrmsg("未配置企业微信 UserID");
            logMapper.insert(entry);
            return;
        }

        try {
            client.sendText(wecomUserId, format(title, content));
            entry.setStatus(WeComMessageLog.STATUS_SENT);
        } catch (WeComException ex) {
            entry.setStatus(WeComMessageLog.STATUS_FAILED);
            entry.setErrcode(ex.getErrcode());
            entry.setErrmsg(ex.getMessage());
            log.warn("wecom send failed, user={}, errcode={}, errmsg={}",
                    user.getEmployeeNo(), ex.getErrcode(), ex.getMessage());
        } catch (Exception ex) {
            entry.setStatus(WeComMessageLog.STATUS_FAILED);
            entry.setErrcode(-1);
            entry.setErrmsg(ex.getMessage());
            log.warn("wecom send failed, user={}", user.getEmployeeNo(), ex);
        } finally {
            logMapper.insert(entry);
        }
    }

    private String resolveUserId(SysUser user) {
        if (StringUtils.hasText(user.getWecomUserId())) {
            return user.getWecomUserId();
        }
        return properties.isUseEmployeeNoFallback() ? user.getEmployeeNo() : null;
    }

    private String format(String title, String content) {
        String safeTitle = StringUtils.hasText(title) ? title : "系统通知";
        String safeContent = content == null ? "" : content;
        return safeTitle + "\n" + safeContent;
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int max = properties.getMaxContentLength();
        if (max > 0 && content.length() > max) {
            return content.substring(0, max);
        }
        return content;
    }
}
