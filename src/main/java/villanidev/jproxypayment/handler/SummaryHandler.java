package villanidev.jproxypayment.handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import villanidev.jproxypayment.cache.RedisCache;
import villanidev.jproxypayment.dto.PaymentSummary;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class SummaryHandler implements HttpHandler {
    private static final String SUMMARY_RESPONSE =
            "{\"default\":{\"totalRequests\":%d,\"totalAmount\":%.2f}," +
                    "\"fallback\":{\"totalRequests\":%d,\"totalAmount\":%.2f}}";

    private final RedisCache redisCache;

    public SummaryHandler(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "Method Not Allowed");
            return;
        }

        try {
            String query = exchange.getRequestURI().getQuery();
            Instant from = null;
            Instant to = null;

            if (query != null) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("from=")) {
                        String fromStr = param.substring(5);
                        from = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(fromStr));
                    } else if (param.startsWith("to=")) {
                        String toStr = param.substring(3);
                        to = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(toStr));
                    }
                }
            }

            PaymentSummary summary = redisCache.getSummary(from, to);
            String response = String.format(SUMMARY_RESPONSE,
                    summary.defaultTotalRequests(),
                    summary.defaultTotalAmount(),
                    summary.fallbackTotalRequests(),
                    summary.fallbackTotalAmount());

            sendResponse(exchange, 200, response);
        } catch (Exception e) {
            sendResponse(exchange, 400, "Invalid request");
        }
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }
}
