package cn.iinti.sekiro3.open.framework.trace;

import cn.iinti.sekiro3.open.framework.StringSplitter;
import cn.iinti.sekiro3.open.framework.ThrowablePrinter;
import cn.iinti.sekiro3.open.framework.safethread.Looper;
import cn.iinti.sekiro3.open.framework.safethread.ValueCallback;
import cn.iinti.sekiro3.open.framework.safethread.ValueCallbackGetter;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 日志采样方案，
 * <ul>
 *     <li>采样日志： 包括搜索和抓取，搜索请求量太大，无法全量存储；抓取请求量大，且无状态</li>
 *     <li>全量日志：售前从预定后的所有流程，所有的售中、售后流程</li>
 * </ul>
 */
public enum EventScene {
    USER_SESSION("search", false),
    SEKIRO_CLIENT("client", false),

    SEKIRO_GROUP("group", true),
    OTHER("OTHER", false);

    @Getter
    private final boolean all;
    @Getter
    private final String name;

    private static final Logger log = LoggerFactory.getLogger("EventTrace");


    EventScene(String name, boolean all) {
        this.all = all;
        this.name = name;
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new Slot();
        }
    }

    private final Slot[] slots = new Slot[30];


    private static class Slot {
        private final AtomicLong time = new AtomicLong(0);
        private Recorder recorder = nopRecorder;
    }

    private static final Recorder nopRecorder = new Recorder("none") {
        @Override
        public void recordEvent(MessageGetter messageGetter, Throwable throwable) {

        }
    };

    private static final Recorder otherRecorder = new RecorderImpl("other", OTHER);


    private static final Looper looper = new Looper("eventRecorder").startLoop();
    private LinkedList<WeakReference<RecorderImpl>> historyRecorders = new LinkedList<>();

    public static void post(Runnable runnable) {
        looper.post(runnable);
    }

    Map<String, List<Event>> fetchEvents() {
        looper.checkLooper();
        Map<String, List<Event>> ret = new HashMap<>();
        for (Slot slot : slots) {
            Recorder recorder = slot.recorder;
            if (!(recorder instanceof RecorderImpl)) {
                continue;
            }
            RecorderImpl recorderImpl = (RecorderImpl) recorder;
            ArrayList<Event> events = new ArrayList<>(recorderImpl.events);
            ret.put(recorderImpl.sessionId, events);
        }
        for (WeakReference<RecorderImpl> reference : historyRecorders) {
            RecorderImpl recorderImpl = reference.get();
            if (recorderImpl != null) {
                ArrayList<Event> events = new ArrayList<>(recorderImpl.events);
                ret.put(recorderImpl.sessionId, events);
            }
        }
        return ret;
    }


    public Recorder acquireRecorder(String sessionId, boolean debug) {
        if (debug || isAll()) {
            return new RecorderImpl(sessionId, this);
        }
        long nowTime = System.currentTimeMillis();
        int slotIndex = ((int) ((nowTime / 1000) % 60)) / 2;
        long timeMinute = nowTime / 60000;

        Slot slot = slots[slotIndex];

        long slotTime = slot.time.get();
        if (slotTime == timeMinute) {
            return nopRecorder;
        }
        if (slot.time.compareAndSet(slotTime, timeMinute)) {
            cacheRecordImpl(slot.recorder);
            slot.recorder = new RecorderImpl(sessionId, this);
            return slot.recorder;
        }
        return nopRecorder;
    }

    private static class RecorderImpl extends Recorder {
        private final LinkedList<Event> events = new LinkedList<>();
        private final String sessionId;
        private final EventScene eventScene;

        RecorderImpl(String sessionId, EventScene eventScene) {
            super(sessionId);
            this.sessionId = sessionId;
            this.eventScene = eventScene;
        }


        @Override
        public void recordEvent(MessageGetter messageGetter, Throwable throwable) {
            looper.post(() -> {
                MDC.put("Scene", eventScene.getName());
                String subTitle = getSubTitle();
                String message = messageGetter.getMessage();

                Collection<String> msgLines = splitMsg(messageGetter.getMessage(), throwable);
                for (String line : msgLines) {
                    if (subTitle != null) {
                        log.info("sessionId:{} subTitle:{} -> {}", sessionId, subTitle, line);
                    } else {
                        log.info("sessionId:{} -> {}", sessionId, line);
                    }
                }
                Event event = new Event(System.currentTimeMillis(), message, throwable);
                events.addLast(event);
            });
        }

        @Override
        public boolean enable() {
            return true;
        }
    }


    public static Recorder nop() {
        return nopRecorder;
    }

    public static Recorder other() {
        return otherRecorder;
    }

    private void cacheRecordImpl(Recorder recorder) {
        if (!(recorder instanceof RecorderImpl)) {
            return;
        }
        looper.post(() -> appendRecordImplImpl((RecorderImpl) recorder));
    }

    private void appendRecordImplImpl(RecorderImpl recorder) {
        if (!looper.inLooper()) {
            looper.post(() -> appendRecordImplImpl(recorder));
            return;
        }
        // 一轮 30个，一分钟
        // 10轮 300个，10分钟
        // session 流程记录最大考虑记录10分钟即可,那么cache最多需要300即可

        // 20210629 300-> 150，trace日志下放到文件中
        if (historyRecorders.size() > 150) {
            LinkedList<WeakReference<RecorderImpl>> newLinkList = new LinkedList<>();
            for (WeakReference<RecorderImpl> reference : historyRecorders) {
                RecorderImpl recorderImpl = reference.get();
                if (recorderImpl == null) {
                    // has been GC
                    continue;
                }
                newLinkList.add(new WeakReference<>(recorderImpl));
                if (newLinkList.size() > 150) {
                    break;
                }
            }
            historyRecorders = newLinkList;
        }

        historyRecorders.addFirst(new WeakReference<>(recorder));

    }

    private static Collection<String> splitMsg(String msg, Throwable throwable) {
        Collection<String> strings = StringSplitter.split(msg, '\n');
        if (throwable == null) {
            return strings;
        }
        if (strings.isEmpty()) {
            // 确保可以被编辑
            strings = new LinkedList<>();
        }
        ThrowablePrinter.printStackTrace(strings, throwable);
        return strings;
    }

    private static final ZoneOffset zoneOffset = ZoneOffset.ofHours(8);
    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static List<String> fetchTrace(Recorder recorder) {
        if (!(recorder instanceof RecorderImpl)) {
            return Collections.emptyList();
        }
        RecorderImpl recorderImpl = (RecorderImpl) recorder;
        return ValueCallbackGetter.syncGet(callback ->
                looper.post(() -> ValueCallback.success(callback,
                        recorderImpl.events.stream()
                                .map(
                                        event -> {
                                            String time = Instant.ofEpochMilli(event.getTimestamp()).atZone(zoneOffset).format(dateTimeFormatter);
                                            return splitMsg(event.getMessage(), event.getThrowable()).stream().map(s -> time + " : " + s);
                                        })
                                .flatMap((Function<Stream<String>, Stream<String>>) it -> it)
                                .collect(Collectors.toList())
                )));


    }
}
