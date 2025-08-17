package villanidev.jproxypayment.service;

import villanidev.jproxypayment.cache.RedisCache;
import villanidev.jproxypayment.dto.PaymentRequest;
import villanidev.jproxypayment.dto.PaymentTask;

import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class PaymentQueueService {

    public static final int NUM_WORKERS = 15;
    private final BlockingQueue<PaymentTask> paymentsQueue = new LinkedBlockingQueue<>();
    private final DistributedProcessorSelector processorSelector;
    private final RedisCache redisCache;
    private final ExecutorService workers;
    private final PaymentProcessor defaultProcessor;
    private final PaymentProcessor fallbackProcessor;

    private final ExecutorService paymentWorkersExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("healthCheckExecutorVthread-", 0L).factory()
    );

    public PaymentQueueService(DistributedProcessorSelector processorSelector,
                               RedisCache redisCache,
                               PaymentProcessor defaultProcessor,
                               PaymentProcessor fallbackProcessor) {
        this.processorSelector = processorSelector;
        this.redisCache = redisCache;
        this.defaultProcessor = defaultProcessor;
        this.fallbackProcessor = fallbackProcessor;
        this.workers = paymentWorkersExecutor;
        startWorkers();
    }

    public void enqueuePayment(PaymentRequest request) {
        paymentsQueue.add(new PaymentTask(request, Instant.now(), 0));
    }

    private void startWorkers() {
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers.submit(this::processPayments);
        }
    }

    private void processPayments() {
        while (true) {
            try {
                PaymentTask task = paymentsQueue.take();
                processPayment(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void processPayment(PaymentTask task) {
        try {
            String bestProcessor = processorSelector.selectBestProcessor();
            PaymentProcessor processor = "default".equals(bestProcessor) ?
                    defaultProcessor : fallbackProcessor;

            processor.processPayment(task)
                    .thenAccept(__ -> {
                        redisCache.recordPayment(bestProcessor, task.request().amount(), task.timestamp());
                    })
                    .exceptionally(e -> {
                        handleProcessingError(task, e);
                        return null;
                    });
        } catch (Exception e) {
            System.err.println(e);
            handleProcessingError(task, e);
        }
    }

    private void handleProcessingError(PaymentTask task, Throwable e) {
        if (task.attempts() < 3) {
            paymentsQueue.add(new PaymentTask(
                    task.request(),
                    task.timestamp(),
                    task.attempts() + 1
            ));
        } else {
            redisCache.recordPayment("failed", task.request().amount(), task.timestamp());
        }
    }
}
