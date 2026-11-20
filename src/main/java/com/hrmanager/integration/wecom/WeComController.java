package com.hrmanager.integration.wecom;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hrmanager.common.ApiResponse;
import com.hrmanager.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/wecom")
@RequiredArgsConstructor
public class WeComController {

    private final WeComProperties properties;
    private final WeComMessageLogMapper logMapper;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", properties.isEnabled());
        data.put("configured", properties.isConfigured());
        data.put("ready", properties.isReady());
        data.put("agentId", properties.getAgentId());
        data.put("useEmployeeNoFallback", properties.isUseEmployeeNoFallback());
        return ApiResponse.ok(data);
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<WeComMessageLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<WeComMessageLog> result = logMapper.selectPage(new Page<>(page, size),
                Wrappers.<WeComMessageLog>lambdaQuery().orderByDesc(WeComMessageLog::getCreatedAt)
                        .orderByDesc(WeComMessageLog::getId));
        return ApiResponse.ok(new PageResult<>(result.getRecords(), result.getTotal(),
                result.getCurrent(), result.getSize()));
    }
}
