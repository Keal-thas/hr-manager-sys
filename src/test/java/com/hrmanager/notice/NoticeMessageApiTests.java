package com.hrmanager.notice;

import com.hrmanager.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class NoticeMessageApiTests extends AbstractApiTest {

    @Test
    void adminCanPublishNoticeToAllUsers() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        String title = "测试全员通知";

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"通知内容\",\"targetType\":\"ALL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetCount").value(4));

        MockHttpSession employee = login("10003", "Emp123456");
        mockMvc.perform(get("/api/my/messages")
                        .session(employee)
                        .param("type", "NOTICE")
                        .param("unreadOnly", "true")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[*].title", hasItem(title)));
    }

    @Test
    void publishRequiresTitle() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        mockMvc.perform(post("/api/admin/notices")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"通知内容\",\"targetType\":\"ALL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("通知标题不能为空"));
    }

    @Test
    void employeeCanMarkAllMessagesRead() throws Exception {
        MockHttpSession employee = login("10003", "Emp123456");

        mockMvc.perform(get("/api/my/messages/unread-count").session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(post("/api/my/messages/read-all").session(employee))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/my/messages/unread-count").session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void templateCanBeCreatedListedAndDisabled() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        String title = "测试模板";

        MvcResult created = mockMvc.perform(post("/api/admin/templates")
                        .session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"模板内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(title))
                .andReturn();
        Integer templateId = JsonPath.read(created.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.id");

        MvcResult listed = mockMvc.perform(get("/api/admin/templates").session(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<String> titles = JsonPath.read(listed.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data[*].title");
        assertTrue(titles.contains(title));

        mockMvc.perform(delete("/api/admin/templates/" + templateId).session(admin))
                .andExpect(status().isOk());

        MvcResult afterDelete = mockMvc.perform(get("/api/admin/templates").session(admin))
                .andExpect(status().isOk())
                .andReturn();
        List<String> remaining = JsonPath.read(afterDelete.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data[*].title");
        assertFalse(remaining.contains(title));
    }
}
