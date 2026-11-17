package com.hrmanager.integration.wecom;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "app.wecom")
public class WeComProperties {

    private boolean enabled;
    private String corpId;
    private String agentId;
    private String secret;
    private String baseUrl = "https://qyapi.weixin.qq.com";
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 5000;
    private boolean useEmployeeNoFallback = true;
    private int maxContentLength = 2000;

    public boolean isConfigured() {
        return StringUtils.hasText(corpId) && StringUtils.hasText(agentId) && StringUtils.hasText(secret);
    }

    public boolean isReady() {
        return enabled && isConfigured();
    }
}
