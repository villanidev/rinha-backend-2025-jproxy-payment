package villanidev.jproxypayment.service.processorgateway;

import villanidev.jproxypayment.dto.PaymentEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DefaultPaymentProcessor implements PaymentProcessor {
    private static final String PAYMENT_REQUEST = "{\"correlationId\":\"%s\",\"amount\":%.2f,\"requestedAt\":\"%s\"}";
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
                .connectTimeout(Duration.ofMillis(200))
                .executor(executor)
                .build();
    }

    @Override
    public CompletableFuture<Void> processPayment(PaymentEvent event) {
        String requestBody = String.format(
                PAYMENT_REQUEST,
                event.request().correlationId(),
                event.request().amount(),
                event.timestamp().toString());

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(paymentUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMillis(200))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() != 200) {
                        System.out.println(Thread.currentThread().getName() +"- Default processor failed to process payment: " + response);
                    }
                });
    }

    @Override
    public double getFee() {
        return FEE_PERCENTAGE;
    }
}
