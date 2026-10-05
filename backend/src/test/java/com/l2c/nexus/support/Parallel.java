package com.l2c.nexus.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Parallel {

    private Parallel() {}

    /** Starts all calls at once and returns the sorted status codes. */
    public static List<Integer> statuses(List<Callable<HttpResponse<String>>> calls)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        try {
            CountDownLatch ready = new CountDownLatch(calls.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (Callable<HttpResponse<String>> call : calls) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return call.call();
                                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS).statusCode());
            }
            return statuses.stream().sorted().toList();
        } finally {
            pool.shutdownNow();
        }
    }
}
