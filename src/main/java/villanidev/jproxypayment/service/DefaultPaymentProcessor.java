package villanidev.jproxypayment.service;

import villanidev.jproxypayment.dto.PaymentRequest;
import villanidev.jproxypayment.dto.PaymentTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultPaymentProcessor implements PaymentProcessor {
    private static final double FEE_PERCENTAGE = 0.05;
    private final HttpClient httpClient;
    private final String paymentUrl;

    ExecutorService executor = Executors.newFixedThreadPool(
            2,
            Thread.ofVirtual().name("defaultPaymentProcessorVthread", 0L).factory()
    );

    public DefaultPaymentProcessor(String paymentUrl) {
        this.paymentUrl = paymentUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(executor)
                .build();
    }

    @Override
    public CompletableFuture<Void> processPayment(PaymentTask task) {
        String requestBody = String.format(
                "{\"correlationId\":\"%s\",\"amount\":%.2f,\"requestedAt\":\"%s\"}",
                task.request().correlationId(),
                task.request().amount(),
                task.timestamp().toString());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(paymentUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(1))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        System.out.println("Failed to process payment");
                    }
                });
    }

    @Override
    public double getFee() {
        return FEE_PERCENTAGE;
    }
}
