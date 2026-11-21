package com.hrmanager.notice;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hrmanager.dept.entity.SysDepartment;
import com.hrmanager.dept.mapper.SysDepartmentMapper;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.notice.mapper.SysMessageMapper;
import com.hrmanager.support.AbstractApiTest;
import com.hrmanager.user.entity.SysUser;
import com.hrmanager.user.mapper.SysUserMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class NoticeTargetApiTests extends AbstractApiTest {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysMessageMapper messageMapper;

    @Autowired
    private SysDepartmentMapper departmentMapper;

    private Long userId(String employeeNo) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getEmployeeNo, employeeNo)
                .last("limit 1"));
        return user == null ? null : user.getId();
    }

    private Long departmentId(String name) {
        return departmentMapper.selectOne(Wrappers.<SysDepartment>lambdaQuery()
                .eq(SysDepartment::getName, name)
                .last("limit 1")).getId();
    }

    private MvcResult publish(MockHttpSession admin, String title, String targetType, String targetIds)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", "通知正文");
        body.put("targetType", targetType);
        body.put("targetIds", targetIds);
        return mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private Long noticeId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");
        return id.longValue();
    }

    private long messageCount(Long noticeId, String employeeNo) {
        return messageMapper.selectCount(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getBizId, noticeId)
                .eq(SysMessage::getType, "NOTICE")
                .eq(SysMessage::getUserId, userId(employeeNo)));
    }

    @Test
    void departmentNoticeTargetsOnlyThatDepartment() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        Long deptId = departmentId("人事服务组");

        MvcResult result = mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"部门通知\",\"content\":\"正文\",\"targetType\":\"DEPT\",\"targetIds\":\""
                                + deptId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(1))
                .andReturn();
        Long noticeId = noticeId(result);

        assertEquals(1, messageCount(noticeId, "10004"));
        assertEquals(0, messageCount(noticeId, "10001"));
        assertEquals(0, messageCount(noticeId, "10002"));
        assertEquals(0, messageCount(noticeId, "10003"));
    }

    @Test
    void userNoticeTargetsOnlySelectedUsers() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        String targetIds = userId("10003") + "," + userId("10004");

        MvcResult result = publish(admin, "指定人员通知", "USER", targetIds);
        Long noticeId = noticeId(result);
        assertEquals(2, ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.data.targetCount")).intValue());

        assertEquals(1, messageCount(noticeId, "10003"));
        assertEquals(1, messageCount(noticeId, "10004"));
        assertEquals(0, messageCount(noticeId, "10001"));
        assertEquals(0, messageCount(noticeId, "10002"));
    }

    @Test
    void allNoticeSkipsDisabledUsers() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        mockMvc.perform(post("/api/admin/users/" + userId("10004") + "/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        MvcResult result = publish(admin, "全员通知", "ALL", null);
        Long noticeId = noticeId(result);

        assertEquals(3, ((Number) JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.data.targetCount")).intValue());
        assertEquals(0, messageCount(noticeId, "10004"));
        assertEquals(1, messageCount(noticeId, "10001"));
        assertEquals(1, messageCount(noticeId, "10003"));
    }

    @Test
    void disabledUserIsExcludedFromExplicitTarget() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        mockMvc.perform(post("/api/admin/users/" + userId("10004") + "/status")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        MvcResult result = publish(admin, "指定人员通知", "USER",
                userId("10003") + "," + userId("10004"));
        Long noticeId = noticeId(result);

        assertEquals(1, messageCount(noticeId, "10003"));
        assertEquals(0, messageCount(noticeId, "10004"));
    }

    @Test
    void noticeRejectsUnknownTargetTypeAndEmptyTarget() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"通知\",\"content\":\"正文\",\"targetType\":\"ROLE\",\"targetIds\":\"1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知目标类型不合法"));

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"通知\",\"content\":\"正文\",\"targetType\":\"USER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知目标为空"));
    }

    @Test
    void noticeRejectsInvalidTitleAndContent() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"通知\",\"targetType\":\"ALL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知内容不能为空"));

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(titleBody(repeat("a", 101), "正文"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知标题不能超过100个字符"));

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(titleBody("通知", repeat("a", 10001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知内容不能超过10000个字符"));
    }

    @Test
    void messageDetailMarksMessageReadAndRejectsOtherUsers() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        MvcResult result = publish(admin, "已读测试通知", "USER", String.valueOf(userId("10003")));
        Long noticeId = noticeId(result);
        SysMessage message = messageMapper.selectOne(Wrappers.<SysMessage>lambdaQuery()
                .eq(SysMessage::getBizId, noticeId)
                .eq(SysMessage::getUserId, userId("10003"))
                .last("limit 1"));
        assertTrue(message != null);
        assertEquals(Boolean.FALSE, message.getReadFlag());

        MockHttpSession employee = login("10003", "Emp123456");
        mockMvc.perform(get("/api/my/messages/" + message.getId()).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("已读测试通知"));

        mockMvc.perform(post("/api/my/messages/" + message.getId() + "/read").session(employee))
                .andExpect(status().isOk());
        assertEquals(Boolean.TRUE, messageMapper.selectById(message.getId()).getReadFlag());

        mockMvc.perform(post("/api/my/messages/" + message.getId() + "/read").session(employee))
                .andExpect(status().isOk());

        MockHttpSession other = login("10004", "Emp123456");
        mockMvc.perform(get("/api/my/messages/" + message.getId()).session(other))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("消息不存在"));
    }

    @Test
    void noticeListDetailAndTemplateLifecycle() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        MvcResult published = publish(admin, "列表详情通知", "ALL", null);
        Long noticeId = noticeId(published);

        mockMvc.perform(get("/api/admin/notices").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mockMvc.perform(get("/api/admin/notices/" + noticeId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("列表详情通知"));
        mockMvc.perform(get("/api/admin/notices/999999").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知不存在"));

        MvcResult created = mockMvc.perform(post("/api/admin/templates")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"模板\",\"content\":\"内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        Integer templateId = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8),
                "$.data.id");

        mockMvc.perform(put("/api/admin/templates/" + templateId)
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"模板已改\",\"content\":\"内容已改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("模板已改"));

        mockMvc.perform(post("/api/admin/templates/" + templateId + "/restore").session(admin))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/templates")
                        .param("status", "ALL")
                        .param("keyword", "模板已改")
                        .session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        mockMvc.perform(put("/api/admin/templates/999999")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"不存在\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板不存在"));
        mockMvc.perform(post("/api/admin/templates/999999/restore").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板不存在"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/admin/templates/999999").session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板不存在"));
    }

    @Test
    void templateValidationRejectsEmptyAndTooLongFields() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/templates")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"content\":\"内容\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板标题和内容不能为空"));

        mockMvc.perform(post("/api/admin/templates")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(titleBody(repeat("a", 201), "内容"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板标题不能超过200个字符"));

        mockMvc.perform(post("/api/admin/templates")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(titleBody("模板", repeat("a", 10001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("模板内容不能超过10000个字符"));
    }

    private Map<String, Object> titleBody(String title, String content) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        return body;
    }

    private String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
