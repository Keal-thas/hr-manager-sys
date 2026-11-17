package com.hrmanager.attendance;

import com.hrmanager.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class AttendanceApiTests extends AbstractApiTest {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Test
    void adminCanListBatchesAndDownloadFiles() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");

        MvcResult batches = mockMvc.perform(get("/api/admin/attendance/batches").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records").isArray())
                .andReturn();
        Integer batchId = JsonPath.read(batches.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.records[0].id");

        mockMvc.perform(get("/api/admin/attendance/batches/" + batchId + "/records").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(greaterThanOrEqualTo(4)));

        mockMvc.perform(get("/api/admin/attendance/template").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX));

        mockMvc.perform(get("/api/admin/attendance/penalty-template").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX));

        mockMvc.perform(get("/api/admin/attendance/batches/" + batchId + "/export").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX));
    }

    @Test
    void employeeCanReadAndConfirmAttendance() throws Exception {
        MockHttpSession employee = login("10004", "Emp123456");

        MvcResult current = mockMvc.perform(get("/api/my/attendance/current").session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNREAD"))
                .andReturn();
        Integer batchId = JsonPath.read(current.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.batchId");

        mockMvc.perform(get("/api/my/attendance/" + batchId).session(employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        mockMvc.perform(post("/api/my/attendance/" + batchId + "/confirm")
                        .session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":true,\"signatureImage\":\"data:image/png;base64,AAAA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void confirmRequiresAgreement() throws Exception {
        MockHttpSession employee = login("10004", "Emp123456");

        MvcResult current = mockMvc.perform(get("/api/my/attendance/current").session(employee))
                .andExpect(status().isOk())
                .andReturn();
        Integer batchId = JsonPath.read(current.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.batchId");

        mockMvc.perform(post("/api/my/attendance/" + batchId + "/confirm")
                        .session(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agree\":false,\"signatureImage\":\"data:image/png;base64,AAAA\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请阅读并同意考勤内容"));
    }

    @Test
    void uploadRejectsInvalidMonth() throws Exception {
        MockHttpSession admin = login("10001", "Admin1234");
        MockMultipartFile file = new MockMultipartFile("file", "attendance.xlsx", XLSX.toString(), new byte[0]);

        mockMvc.perform(multipart("/api/admin/attendance/upload")
                        .file(file)
                        .param("year", "2026")
                        .param("month", "13")
                        .param("mode", "NEW")
                        .session(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("考勤月份不合法"));
    }
}
