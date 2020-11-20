package com.virjar.sekiro.api;

import com.virjar.sekiro.log.SekiroLogger;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.util.internal.ConcurrentSet;

public class HandlerThreadPool {
    private static HandlerThreadPool instance;
    private final static AtomicLong idSeed = new AtomicLong(0);

    static {
        instance = new HandlerThreadPool();
        // 默认启动两个线程工作
        instance.increaseWorker();
        instance.increaseWorker();
    }

    /**
     * 线程最多空转30s
     */
    private static int idleSecond = 30;

    /**
     * 默认最多15个线程，可以设置到100个
     */
    private static int maxWorkSize = 15;

    /**
     * 超过三个任务排队就会增加线程池数量
     */
    private static int maxPendingTaskSize = 3;

    /**
     * 任务等待时间超过5s就会增加线程池数量
     */
    private static int maxWaitingSecond = 5;

    public static void setIdleSecond(int idleSecond) {
        if (idleSecond < 0) {
            return;
        }
        HandlerThreadPool.idleSecond = idleSecond;
    }

    public static void setMaxWorkSize(int maxWorkSize) {
        if (maxWorkSize > 100) {
            SekiroLogger.warn("the sekiro worker can not grater than 100");
            return;
        }

        if (maxWorkSize < 2) {
            SekiroLogger.warn("the sekiro worker can not less than 2");
            return;
        }
        HandlerThreadPool.maxWorkSize = maxWorkSize;
    }

    public static void setMaxPendingTaskSize(int maxPendingTaskSize) {
        if (maxPendingTaskSize < 0) {
            return;
        }
        HandlerThreadPool.maxPendingTaskSize = maxPendingTaskSize;
    }

    public static void setMaxWaitingSecond(int maxWaitingSecond) {
        if (maxWaitingSecond < 0) {
            return;
        }
        HandlerThreadPool.maxWaitingSecond = maxWaitingSecond;
    }


    private static class TaskHolder {
        TaskRunner taskRunner;
        long enqueueTimestamp;
        SekiroResponse sekiroResponse;

        public TaskHolder(TaskRunner taskRunner, SekiroResponse sekiroResponse) {
            this.taskRunner = taskRunner;
            this.sekiroResponse = sekiroResponse;
            enqueueTimestamp = System.currentTimeMillis();
        }
    }


    private class TaskExecutorThread extends Thread {
        public TaskExecutorThread() {
            super("sekiro-worker-" + idSeed.getAndIncrement());
            setDaemon(true);
            workers.add(this);
            start();
        }

        private void work() {
            while (!isInterrupted()) {
                TaskHolder taskHolder;
                try {
                    taskHolder = taskQueue.poll(idleSecond, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    return;
                }
                if (taskHolder == null) {
                    if (workers.size() <= 2) {
                        // 有两个保底线程
                        continue;
                    }
                    //任务为空，线程空转了20s，停止线程
                    return;
                }

                if (System.currentTimeMillis() - taskHolder.enqueueTimestamp
                        > maxWaitingSecond * 1000 || taskQueue.size() > maxPendingTaskSize
                ) {
                    SekiroLogger.warn("pending task size: " + taskQueue.size());
                    increaseWorker();
                }

                try {
                    taskHolder.taskRunner.run();
                } catch (Throwable throwable) {
                    SekiroLogger.error("handle task", throwable);
                    taskHolder.sekiroResponse.failed(CommonRes.statusError, throwable);
                }
            }
        }

        @Override
        public void run() {
            super.run();
            try {
                work();
            } finally {
                workers.remove(this);
            }
        }
    }

    private void increaseWorker() {
        if (workers.size() > maxWorkSize) {
            SekiroLogger.warn("not enough thread resource to execute sekiro request,please setup your custom thread pool!!");
            return;
        }
        new TaskExecutorThread();
    }

    private final BlockingQueue<TaskHolder> taskQueue = new LinkedBlockingQueue<>();
    private final Set<TaskExecutorThread> workers = new ConcurrentSet<>();


    public static void post(TaskRunner taskRunner, SekiroResponse sekiroResponse) {
        instance.taskQueue.add(new TaskHolder(
                taskRunner, sekiroResponse
        ));
        if (instance.workers.size() < 2 || instance.taskQueue.size() > maxPendingTaskSize) {
            instance.increaseWorker();
        }

        if (instance.taskQueue.size() > 10) {
            SekiroLogger.warn("too many pending task submit,please setup your custom thread pool!! pending task size:" + instance.taskQueue.size());
        }

    }

    public interface TaskRunner {
        void run();
    }
}
