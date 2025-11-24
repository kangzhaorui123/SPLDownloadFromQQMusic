// TaskExecutor.java
package com.kenny.spldownloader.manager;

import android.util.Log;
import java.util.concurrent.*;

public class TaskExecutor {
    private static final String TAG = "TaskExecutor";
    private static TaskExecutor instance;

    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutor;

    private TaskExecutor() {
        this.executorService = Executors.newFixedThreadPool(3);
        this.scheduledExecutor = Executors.newScheduledThreadPool(1);
    }

    public static synchronized TaskExecutor getInstance() {
        if (instance == null) {
            instance = new TaskExecutor();
        }
        return instance;
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executorService);
    }

    public <T> CompletableFuture<T> submitWithRetry(Callable<T> task, int maxRetries) {
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                try {
                    return task.call();
                } catch (Exception e) {
                    lastException = e;
                    Log.w(TAG, "任务执行失败，第 " + attempt + " 次重试，错误: " + e.getMessage());

                    if (attempt < maxRetries) {
                        try {
                            Thread.sleep(com.kenny.spldownloader.config.AppConfig.RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new CompletionException(ie);
                        }
                    }
                }
            }

            throw new CompletionException("任务执行失败，已达到最大重试次数", lastException);
        }, executorService);
    }

    public void shutdown() {
        executorService.shutdown();
        scheduledExecutor.shutdown();

        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}