package com.virjar.sekiro.server.netty;

import com.virjar.sekiro.server.netty.websocket.WebSocketMessageAggregator;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TimeoutTaskCleaner {

    private static final long taskCleanDuration = 60000;

    @Scheduled(fixedRate = taskCleanDuration)
    public void clean() {
        try {
            doClean();
        } catch (Exception e) {
            log.error("clean timeout task failed", e);
        }
    }

    private void doClean() {
        //60s之前的任务，都给remove调
        //TaskRegistry.getInstance().cleanBefore(System.currentTimeMillis() - taskCleanDuration * 2);
        WebSocketMessageAggregator.cleanBefore(System.currentTimeMillis() - taskCleanDuration * 3);
    }
}
