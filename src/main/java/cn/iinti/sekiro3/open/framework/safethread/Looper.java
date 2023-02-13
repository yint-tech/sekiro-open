package cn.iinti.sekiro3.open.framework.safethread;


import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * 单线程事件循环模型，用来避免一致性问题
 */
@Slf4j
public class Looper implements Executor {

    private final LinkedBlockingDeque<Runnable> taskQueue = new LinkedBlockingDeque<>();

    private final LoopThread loopThread;
    private final long createTimestamp = System.currentTimeMillis();


    private static Looper lowPriorityLooper;


    /**
     * 获取一个全局的低优looper
     *
     * @return looper
     */
    public static Looper getLowPriorityLooper() {
        if (lowPriorityLooper != null) {
            return lowPriorityLooper;
        }
        synchronized (Looper.class) {
            lowPriorityLooper = new Looper("lowPriorityLooper").startLoop();
        }
        return lowPriorityLooper;
    }

    /**
     * 让looper拥有延时任务的能力
     */
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public Looper(String looperName) {


        loopThread = new LoopThread(looperName);
        loopThread.setDaemon(true);
    }


    public Looper startLoop() {
        loopThread.start();
        return this;
    }

    public void post(Runnable runnable) {
        post(runnable, false);
    }

    public void offerLast(Runnable runnable) {
        taskQueue.addLast(runnable);
    }

    public void post(Runnable runnable, boolean first) {
        if (!loopThread.isAlive()) {
            if (System.currentTimeMillis() - createTimestamp > 60000) {
                log.warn("post task before looper startup,do you call :startLoop??", new Throwable());
            }
            runnable.run();
            return;
        }
        if (inLooper()) {
            runnable.run();
            return;
        }
        if (first) {
            taskQueue.offerFirst(runnable);
        } else {
            taskQueue.add(runnable);
        }
    }

    public void postDelay(Runnable runnable, long delay) {
        if (delay <= 0) {
            post(runnable);
            return;
        }
        if (!loopThread.isAlive()) {
            //todo 这是应该是有bug，先加上这一行日志
            if (System.currentTimeMillis() - createTimestamp > 60000) {
                log.warn("post task before looper startup,do you call :startLoop??", new Throwable());
            }
        }
        scheduler.schedule(() -> post(runnable), delay, TimeUnit.MILLISECONDS);
    }

    public Looper fluentScheduleWithRate(Runnable runnable, long rate) {
        scheduleWithRate(runnable, rate);
        return this;
    }

    public FixRateScheduleHandle scheduleWithRate(Runnable runnable, long rate) {
        return scheduleWithRate(runnable, Long.valueOf(rate));
    }

    /**
     * 这个接口，可以支持非固定速率
     */
    public FixRateScheduleHandle scheduleWithRate(Runnable runnable, Number rate) {
        FixRateScheduleHandle fixRateScheduleHandle = new FixRateScheduleHandle(runnable, rate);
//        if (rate.longValue() > 0) {
//            post(runnable);
//        }
        postDelay(fixRateScheduleHandle, rate.longValue());
        return fixRateScheduleHandle;
    }

    @Override
    public void execute(Runnable command) {
        post(command);
    }


    public class FixRateScheduleHandle implements Runnable {
        private final Runnable runnable;
        private final Number rate;
        private boolean running;


        FixRateScheduleHandle(Runnable runnable, Number rate) {
            this.runnable = runnable;
            this.running = true;
            this.rate = rate;
        }

        public void cancel() {
            this.running = false;
        }

        @Override
        public void run() {
            if (running && rate.longValue() > 0) {
                postDelay(this, rate.longValue());
            }
            runnable.run();
        }

    }

    public boolean inLooper() {
        return Thread.currentThread().equals(loopThread) || !loopThread.isAlive();
    }

    public void checkLooper() {
        if (!inLooper()) {
            throw new IllegalStateException("run task not in looper");
        }
    }

    private class LoopThread extends Thread {
        LoopThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    taskQueue.take().run();
                } catch (InterruptedException interruptedException) {
                    return;
                } catch (Throwable throwable) {
                    log.error("group event loop error", throwable);
                }
            }
        }
    }

    public void close() {
        loopThread.interrupt();
    }
}
