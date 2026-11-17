package com.hrmanager.integration.wecom;

import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeComNotifierTest {

    private final WeComClient client = Mockito.mock(WeComClient.class);
    private final SysUserMapper userMapper = Mockito.mock(SysUserMapper.class);
    private final WeComMessageLogMapper logMapper = Mockito.mock(WeComMessageLogMapper.class);

    @Test
    void logsSentMessage() {
        WeComProperties properties = properties(true, false);
        WeComNotifier notifier = new WeComNotifier(properties, client, userMapper, logMapper);
        SysUser user = user(10004L, "10004", "wecom-10004");
        when(userMapper.selectById(10004L)).thenReturn(user);

        notifier.send(10004L, "NOTICE", "标题", "内容");

        verify(client).sendText(Mockito.eq("wecom-10004"), Mockito.contains("标题"));
        ArgumentCaptor<WeComMessageLog> captor = ArgumentCaptor.forClass(WeComMessageLog.class);
        verify(logMapper).insert(captor.capture());
        assertEquals(WeComMessageLog.STATUS_SENT, captor.getValue().getStatus());
    }

    @Test
    void logsSkippedWhenUserHasNoMapping() {
        WeComProperties properties = properties(true, false);
        WeComNotifier notifier = new WeComNotifier(properties, client, userMapper, logMapper);
        SysUser user = user(10004L, "10004", null);
        when(userMapper.selectById(Mockito.anyLong())).thenReturn(user);

        notifier.send(10004L, "NOTICE", "标题", "内容");

        ArgumentCaptor<WeComMessageLog> captor = ArgumentCaptor.forClass(WeComMessageLog.class);
        verify(logMapper).insert(captor.capture());
        assertEquals(WeComMessageLog.STATUS_SKIPPED, captor.getValue().getStatus());
    }

    @Test
    void logsFailureWhenClientFails() {
        WeComProperties properties = properties(true, false);
        WeComNotifier notifier = new WeComNotifier(properties, client, userMapper, logMapper);
        SysUser user = user(10004L, "10004", "wecom-10004");
        when(userMapper.selectById(Mockito.anyLong())).thenReturn(user);
        Mockito.doThrow(new WeComException(40013, "invalid user")).when(client)
                .sendText(Mockito.anyString(), Mockito.anyString());

        notifier.send(10004L, "NOTICE", "标题", "内容");

        ArgumentCaptor<WeComMessageLog> captor = ArgumentCaptor.forClass(WeComMessageLog.class);
        verify(logMapper).insert(captor.capture());
        assertEquals(WeComMessageLog.STATUS_FAILED, captor.getValue().getStatus());
        assertEquals(40013, captor.getValue().getErrcode());
    }

    @Test
    void skipsWhenDisabled() {
        WeComProperties properties = properties(false, false);
        WeComNotifier notifier = new WeComNotifier(properties, client, userMapper, logMapper);

        notifier.send(10004L, "NOTICE", "标题", "内容");

        Mockito.verifyNoInteractions(client, userMapper, logMapper);
    }

    private WeComProperties properties(boolean enabled, boolean fallback) {
        WeComProperties properties = new WeComProperties();
        properties.setEnabled(enabled);
        properties.setCorpId("corp");
        properties.setAgentId("1000002");
        properties.setSecret("secret");
        properties.setUseEmployeeNoFallback(fallback);
        return properties;
    }

    private SysUser user(Long id, String employeeNo, String wecomUserId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setEmployeeNo(employeeNo);
        user.setName("李四");
        user.setWecomUserId(wecomUserId);
        return user;
    }
}
