package com.hrmanager.integration.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WeComClientTest {

    private MockRestServiceServer server;
    private WeComClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        WeComProperties properties = new WeComProperties();
        properties.setEnabled(true);
        properties.setCorpId("corp");
        properties.setAgentId("1000002");
        properties.setSecret("secret");
        properties.setBaseUrl("https://qyapi.weixin.qq.com");
        client = new WeComClient(restTemplate, new ObjectMapper(), properties);
    }

    @Test
    void sendsTextAndCachesToken() {
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"token-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=token-1"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.touser").value("10004"))
                .andExpect(jsonPath("$.agentid").value(1000002))
                .andExpect(jsonPath("$.text.content", containsString("hello")))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=token-1"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        client.sendText("10004", "hello");
        client.sendText("10004", "hello again");

        server.verify();
    }

    @Test
    void retriesOnceWhenTokenIsInvalid() {
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"token-1\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=token-1"))
                .andRespond(withSuccess("{\"errcode\":42001,\"errmsg\":\"access_token expired\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith("https://qyapi.weixin.qq.com/cgi-bin/gettoken")))
                .andRespond(withSuccess("{\"errcode\":0,\"access_token\":\"token-2\",\"expires_in\":7200}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://qyapi.weixin.qq.com/cgi-bin/message/send?access_token=token-2"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\"}", MediaType.APPLICATION_JSON));

        client.sendText("10004", "hello");

        server.verify();
    }
}
