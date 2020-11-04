package com.virjar.sekiro.server.controller;


import com.virjar.sekiro.api.CommonRes;
import com.virjar.sekiro.server.netty.ChannelRegistry;
import com.virjar.sekiro.server.netty.NatClient;
import com.virjar.sekiro.server.netty.http.ContentType;
import com.virjar.sekiro.server.util.ReturnUtil;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import external.com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by virjar on 2019/2/23.
 */
@RestController
@Slf4j
public class SekiroServerController {

    @GetMapping("/natChannelStatus")
    @ResponseBody
    public CommonRes<?> natChannelStatus(String group) {
        if (StringUtils.isBlank(group)) {
            return CommonRes.failed("the param:{group} not present");
        }
        Map<String, List<String>> stringListMap = ChannelRegistry.getInstance().channelStatus(group);
        return CommonRes.success(stringListMap);
    }


    @GetMapping("/groupList")
    @ResponseBody
    public CommonRes<?> groupList() {
        List<String> stringListMap = ChannelRegistry.getInstance().channelList();
        return CommonRes.success(stringListMap);
    }

    @GetMapping("/disconnectClient")
    @ResponseBody
    public CommonRes<?> disconnectClient(String group, String clientId) {
        return CommonRes.success(ChannelRegistry.getInstance().forceDisconnect(group, clientId));
    }

    @GetMapping("/disableClient")
    @ResponseBody
    public CommonRes<?> disableClient(String group, String clientId) {
        return ChannelRegistry.getInstance().disable(group, clientId);
    }

    @GetMapping("/enableClient")
    @ResponseBody
    public CommonRes<?> enableClient(String group, String clientId) {
        return ChannelRegistry.getInstance().enable(group, clientId);
    }

    @RequestMapping(value = "/invoke", method = {RequestMethod.GET, RequestMethod.POST})
    public void invoke(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        String contentType = httpServletRequest.getContentType();

        Map<String, String[]> parameterMap = httpServletRequest.getParameterMap();
        JSONObject requestJson = new JSONObject();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] value = entry.getValue();
            if (value == null || value.length == 0) {
                continue;
            }
            requestJson.put(entry.getKey(), value[0]);
        }
        if (httpServletRequest.getMethod().equalsIgnoreCase("post")
                && StringUtils.containsIgnoreCase(contentType, "application/json")) {
            ContentType contentTypeObject = ContentType.from(contentType);

            assert contentTypeObject != null;
            String charset = contentTypeObject.getCharset();
            if (StringUtils.isBlank(charset)) {
                charset = StandardCharsets.UTF_8.name();
            }
            try {
                String requestJSONBody = IOUtils.toString(httpServletRequest.getInputStream(), charset);
                JSONObject jsonObject = JSONObject.parseObject(requestJSONBody);
                for (String key : jsonObject.keySet()) {
                    requestJson.put(key, jsonObject.get(key));
                }

            } catch (IOException e) {
                log.error("error for decode http request", e);
                ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed(e));
                return;
            }
        }


        String invokeTimeoutString = requestJson.getString("invoke_timeout");
        if (StringUtils.isBlank(invokeTimeoutString)) {
            //正确写法应该是invoke_timeout，由于历史原因存在错别字
            invokeTimeoutString = requestJson.getString("invoke_timeOut");
        }

        int timeOut = NumberUtils.toInt(invokeTimeoutString);
        String group = requestJson.getString("group");
        String bindClient = requestJson.getString("bindClient");

        if (StringUtils.isBlank(group)) {
            ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed("the param {group} not presented"));
            return;
        }

        if (timeOut < 500) {
            //默认15s的超时时间
            timeOut = 15000;
        }

        NatClient natClient;
        if (StringUtils.isNotBlank(bindClient)) {
            natClient = ChannelRegistry.getInstance().queryByClient(group, bindClient);
            if (natClient == null || !natClient.getCmdChannel().isActive()) {
                ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed("device offline"));
                return;
            }
        } else {
            natClient = ChannelRegistry.getInstance().allocateOne(group);
        }
        if (natClient == null) {
            ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed("no enable device online"));
            return;
        }
        natClient.forward(requestJson.toJSONString(), timeOut, httpServletResponse);
    }


}
