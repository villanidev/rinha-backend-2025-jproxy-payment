package villanidev.jproxypayment.service.payment;

import villanidev.jproxypayment.cache.RedisCacheClient;
import villanidev.jproxypayment.dto.PaymentRequest;
import villanidev.jproxypayment.dto.PaymentEvent;
import villanidev.jproxypayment.service.processorgateway.DistributedProcessorSelector;
import villanidev.jproxypayment.service.processorgateway.PaymentProcessor;

import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.*;

public class PaymentQueueService {

    private static final int NUM_WORKERS = 10;
    private static final int MAX_ATTEMPTS = 5;
    private final BlockingQueue<PaymentEvent> paymentsQueue = new PriorityBlockingQueue<>(
            20_000,
            Comparator.comparingInt(PaymentEvent::attempts).reversed()
    );
    private final DistributedProcessorSelector processorSelector;
    private final RedisCacheClient redisCacheClient;
    private final ExecutorService workers;
    private final PaymentProcessor defaultProcessor;
    private final PaymentProcessor fallbackProcessor;

    private final ExecutorService paymentWorkersExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("paymentWorkersExecutorVthread-", 0L).factory()
    );

    public PaymentQueueService(DistributedProcessorSelector processorSelector,
                               RedisCacheClient redisCacheClient,
                               PaymentProcessor defaultProcessor,
                               PaymentProcessor fallbackProcessor) {
        this.processorSelector = processorSelector;
        this.redisCacheClient = redisCacheClient;
        this.defaultProcessor = defaultProcessor;
        this.fallbackProcessor = fallbackProcessor;
        this.workers = paymentWorkersExecutor;
        startWorkers();
    }

    public void enqueuePayment(PaymentRequest request) {
        paymentsQueue.add(new PaymentEvent(request, Instant.now(), 0));
    }

    private void startWorkers() {
        for (int i = 0; i < NUM_WORKERS; i++) {
            workers.submit(this::processPayments);
        }
    }

    private void processPayments() {
        while (true) {
            try {
                PaymentEvent event = paymentsQueue.take();
                processPayment(event);
            } catch (InterruptedException e) {
                System.err.println("Erro ao consumir fila: " + e);
            }
        }
    }

    private void processPayment(PaymentEvent event) {
        try {
            String bestProcessor = processorSelector.selectBestProcessor();
            PaymentProcessor processor = "default".equals(bestProcessor) ?
                    defaultProcessor : fallbackProcessor;

            processor.processPayment(event)
                    .thenAccept(__ -> {
                        redisCacheClient.savePayment(bestProcessor, event.request().amount(), event.timestamp());
                    })
                    .exceptionally(e -> {
                        handleProcessingError(event, e);
                        return null;
                    });
        } catch (Exception e) {
            handleProcessingError(event, e);
        }
    }

    private void handleProcessingError(PaymentEvent task, Throwable e) {
        System.err.println("handleProcessingError: "+ e);
        /*if (task.attempts() < MAX_ATTEMPTS) {
            paymentsQueue.add(new PaymentEvent(
                    task.request(),
                    task.timestamp(),
                    task.attempts() + 1
            ));
        } else {
            redisCacheClient.savePayment("failed", task.request().amount(), task.timestamp());
            System.err.println("Failed to process payment - too many retries: "+ e);
        }*/

        paymentsQueue.add(new PaymentEvent(
                task.request(),
                task.timestamp(),
                task.attempts() + 1
        ));
    }
}
