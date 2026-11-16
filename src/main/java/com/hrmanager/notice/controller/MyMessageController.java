package com.hrmanager.notice.controller;

import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.PageResult;
import com.hrmanager.common.SessionUtil;
import com.hrmanager.notice.entity.SysMessage;
import com.hrmanager.notice.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/my/messages")
@RequiredArgsConstructor
public class MyMessageController {

    private final MessageService messageService;

    @GetMapping
    public ApiResponse<PageResult<SysMessage>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean unreadOnly,
            HttpServletRequest request) {
        return ApiResponse.ok(messageService.page(SessionUtil.current(request).getId(), page, size, type, unreadOnly));
    }

    @GetMapping("/{id}")
    public ApiResponse<SysMessage> detail(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.ok(messageService.get(id, SessionUtil.current(request).getId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount(HttpServletRequest request) {
        long count = messageService.unreadCount(SessionUtil.current(request).getId());
        return ApiResponse.ok(Collections.singletonMap("count", count));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, HttpServletRequest request) {
        messageService.read(id, SessionUtil.current(request).getId());
        return ApiResponse.ok();
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(HttpServletRequest request) {
        messageService.readAll(SessionUtil.current(request).getId());
        return ApiResponse.ok();
    }
}
