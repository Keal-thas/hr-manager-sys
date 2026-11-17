package com.hrmanager.integration.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeComClient {

    private final RestTemplate weComRestTemplate;
    private final ObjectMapper objectMapper;
    private final WeComProperties properties;

    private volatile String accessToken;
    private volatile long expiresAt;

    public void sendText(String toUser, String content) {
        if (!properties.isReady()) {
            throw new WeComException("企业微信未启用或未配置");
        }
        if (!StringUtils.hasText(toUser)) {
            throw new WeComException("企业微信 UserID 为空");
        }
        try {
            doSend(toUser, content, token());
        } catch (WeComException ex) {
            if (!ex.isTokenInvalid()) {
                throw ex;
            }
            invalidateToken();
            doSend(toUser, content, token());
        }
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    private void doSend(String toUser, String content, String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("touser", toUser);
        body.put("msgtype", "text");
        body.put("agentid", agentId());
        Map<String, String> text = new LinkedHashMap<>();
        text.put("content", truncate(content));
        body.put("text", text);
        body.put("safe", 0);

        String url = properties.getBaseUrl() + "/cgi-bin/message/send?access_token={token}";
        JsonNode response = weComRestTemplate.postForObject(url, body, JsonNode.class, token);
        checkResponse(response, "send message");
    }

    private String token() {
        long now = System.currentTimeMillis();
        String cached = accessToken;
        if (StringUtils.hasText(cached) && expiresAt > now) {
            return cached;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(accessToken) && expiresAt > now) {
                return accessToken;
            }
            String url = properties.getBaseUrl() + "/cgi-bin/gettoken?corpid={corpId}&corpsecret={secret}";
            try {
                ResponseEntity<JsonNode> response = weComRestTemplate.getForEntity(
                        url, JsonNode.class, properties.getCorpId(), properties.getSecret());
                JsonNode body = response.getBody();
                checkResponse(body, "get token");
                String token = body.path("access_token").asText();
                int expiresIn = body.path("expires_in").asInt(7200);
                if (!StringUtils.hasText(token)) {
                    throw new WeComException("企业微信 access_token 为空");
                }
                accessToken = token;
                expiresAt = System.currentTimeMillis() + Math.max(60, expiresIn - 120) * 1000L;
                log.info("wecom access token refreshed, expires in {}s", expiresIn);
                return accessToken;
            } catch (WeComException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new WeComException("获取企业微信 access_token 失败", ex);
            }
        }
    }

    private void checkResponse(JsonNode body, String action) {
        if (body == null) {
            throw new WeComException("企业微信响应为空: " + action);
        }
        int errcode = body.path("errcode").asInt(-1);
        String errmsg = body.path("errmsg").asText("unknown error");
        if (errcode != 0) {
            throw new WeComException(errcode, "企业微信" + action + "失败: " + errmsg);
        }
    }

    private int agentId() {
        try {
            return Integer.parseInt(properties.getAgentId());
        } catch (NumberFormatException ex) {
            throw new WeComException("企业微信 AgentId 必须是数字");
        }
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

    private void invalidateToken() {
        accessToken = null;
        expiresAt = 0;
    }
}
