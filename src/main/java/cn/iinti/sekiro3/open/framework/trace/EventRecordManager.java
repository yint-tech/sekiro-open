package cn.iinti.sekiro3.open.framework.trace;


import cn.iinti.sekiro3.open.framework.safethread.ValueCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 用来采样trace事件，排查异步问题
 */
public class EventRecordManager {

    public static void fetchEvents(ValueCallback<Map<String, Map<String, List<Event>>>> valueCallback) {
        final Map<String, Map<String, List<Event>>> ret = new HashMap<>();
        EventScene.post(() -> {
            for (EventScene eventScene : EventScene.values()) {
                ret.put(eventScene.getName(), eventScene.fetchEvents());
            }
            valueCallback.onReceiveValue(ValueCallback.Value.success(ret));
        });
    }

    public static Recorder acquireRecorder(String sessionId, EventScene eventScene) {
        return acquireRecorder(sessionId, false, eventScene);
    }

    public static Recorder acquireRecorder(String sessionId) {
        return acquireRecorder(sessionId, false, EventScene.OTHER);
    }

    public static Recorder acquireRecorder(String sessionId, boolean debug, EventScene eventScene) {
        return eventScene.acquireRecorder(sessionId, debug);
    }


    public static Recorder nop() {
        return EventScene.nop();
    }
}
