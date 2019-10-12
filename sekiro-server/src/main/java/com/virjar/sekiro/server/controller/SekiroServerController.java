package com.virjar.sekiro.server.controller;


import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.virjar.sekiro.api.CommonRes;
import com.virjar.sekiro.server.netty.ChannelRegistry;
import com.virjar.sekiro.server.netty.NatClient;
import com.virjar.sekiro.server.util.CommonUtil;
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
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
        List<String> stringListMap = ChannelRegistry.getInstance().channelStatus(group);
        return CommonRes.success(stringListMap);
    }

    @GetMapping("/groupList")
    @ResponseBody
    public CommonRes<?> groupList() {
        List<String> stringListMap = ChannelRegistry.getInstance().channelList();
        return CommonRes.success(stringListMap);
    }

    @RequestMapping(value = "/invoke", method = {RequestMethod.GET, RequestMethod.POST})
    public void invoke(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        String contentType = httpServletRequest.getContentType();
        int timeOut = NumberUtils.toInt(httpServletRequest.getParameter("invoke_timeOut"));
        String group = httpServletRequest.getParameter("group");
        String bindClient = httpServletRequest.getParameter("bindClient");
        String requestBody = CommonUtil.joinParam(httpServletRequest.getParameterMap());
        if (httpServletRequest.getMethod().equalsIgnoreCase("post")
                && StringUtils.containsIgnoreCase(contentType, "application/json;charset=utf8")) {
            try {
                requestBody = IOUtils.toString(httpServletRequest.getInputStream());
            } catch (IOException e) {
                log.error("error for decode http request", e);
                ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed(e));
                return;
            }
        }

        if (timeOut < 1) {
            //默认5s的超时时间
            timeOut = 5000;
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
            ReturnUtil.writeRes(httpServletResponse, ReturnUtil.failed("no device online"));
            return;
        }
        natClient.forward(requestBody, timeOut, httpServletResponse);
    }


}
