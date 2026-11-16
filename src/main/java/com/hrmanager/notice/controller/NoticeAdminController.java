package com.hrmanager.notice.controller;

import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.PageResult;
import com.hrmanager.common.SessionUtil;
import com.hrmanager.notice.entity.MessageNotice;
import com.hrmanager.notice.entity.NoticeTemplate;
import com.hrmanager.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class NoticeAdminController {

    private final NoticeService noticeService;

    @GetMapping("/notices")
    public ApiResponse<PageResult<MessageNotice>> notices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(noticeService.page(page, size));
    }

    @GetMapping("/notices/{id}")
    public ApiResponse<MessageNotice> notice(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.get(id));
    }

    @PostMapping("/notices")
    public ApiResponse<Map<String, Object>> publish(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        return ApiResponse.ok(noticeService.publish(body, SessionUtil.current(request).getId()));
    }

    @GetMapping("/templates")
    public ApiResponse<List<NoticeTemplate>> templates() {
        return ApiResponse.ok(noticeService.templates());
    }

    @PostMapping("/templates")
    public ApiResponse<NoticeTemplate> createTemplate(@RequestBody NoticeTemplate body) {
        return ApiResponse.ok(noticeService.createTemplate(body));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<NoticeTemplate> updateTemplate(@PathVariable Long id, @RequestBody NoticeTemplate body) {
        return ApiResponse.ok(noticeService.updateTemplate(id, body));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> disableTemplate(@PathVariable Long id) {
        noticeService.disableTemplate(id);
        return ApiResponse.ok();
    }
}
