package villanidev.jproxypayment.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import villanidev.jproxypayment.dto.PaymentRequest;
import villanidev.jproxypayment.service.PaymentQueueService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PaymentHandler implements HttpHandler {
    private final ExecutorService asyncProcessor = Executors.newFixedThreadPool(
            15,
            Thread.ofVirtual().name("asyncVthread-", 0L).factory()
    );
    private final PaymentQueueService paymentQueueService;

    public PaymentHandler(PaymentQueueService paymentQueueService) {
        this.paymentQueueService = paymentQueueService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        asyncProcessor.submit(() -> processAsync(exchange));
        sendResponse(exchange, 202, "");
    }

    private void processAsync(HttpExchange exchange) {
        try (exchange; InputStream is = exchange.getRequestBody()) {
            String requestBody = new String(is.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s", "");

            // Parsing manual otimizado
            String idStr = requestBody.substring(18, 54);

            int amountStart = requestBody.indexOf("\"amount\":") + 9;
            int amountEnd = requestBody.indexOf("}", amountStart);
            String amountStr = requestBody.substring(amountStart, amountEnd).trim();

            UUID correlationId = UUID.fromString(idStr);
            double amount = Double.parseDouble(amountStr);

            paymentQueueService.enqueuePayment(new PaymentRequest(correlationId, amount));
        } catch (Exception e) {
            System.err.println("Error processing payment: " + e.getMessage());
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.sendResponseHeaders(statusCode, response.length());
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
}
