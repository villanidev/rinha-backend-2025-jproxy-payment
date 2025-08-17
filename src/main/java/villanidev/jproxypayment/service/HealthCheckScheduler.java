package villanidev.jproxypayment.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthCheckScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("schedulerVthread-", 0L).factory()
    );

    private final ExecutorService healthCheckExecutor = Executors.newFixedThreadPool(
            2,
            Thread.ofVirtual().name("healthCheckExecutorVthread-", 0L).factory()
    );
    private final HttpClient httpClient;
    private final DistributedProcessorSelector processorSelector;
    private final String defaultProcessorUrl;
    private final String fallbackProcessorUrl;

    public HealthCheckScheduler(DistributedProcessorSelector processorSelector,
                                String defaultProcessorUrl,
                                String fallbackProcessorUrl) {
        this.processorSelector = processorSelector;
        this.defaultProcessorUrl = defaultProcessorUrl;
        this.fallbackProcessorUrl = fallbackProcessorUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .executor(healthCheckExecutor)
                .build();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::checkDefaultHealth, 0, 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::checkFallbackHealth, 0, 5, TimeUnit.SECONDS);
    }

    private void checkDefaultHealth() {
        checkProcessorHealth("default", defaultProcessorUrl + "/payments/service-health");
    }

    private void checkFallbackHealth() {
        checkProcessorHealth("fallback", fallbackProcessorUrl + "/payments/service-health");
    }

    private void checkProcessorHealth(String processor, String healthUrl) {
        System.out.println(Thread.currentThread().getName() + "- Monitoring payment processor: " + processor +  " health at: " + LocalDateTime.now());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(healthUrl))
                    .timeout(Duration.ofSeconds(2))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Resultado ok");
                parseAndUpdateHealth(processor, response.body());
            } else if (response.statusCode() == 429) {
                System.out.println("Caiu no rate limiting");
            } else {
                System.out.println("Servico aparentemente fora, status: " + response.statusCode());
                processorSelector.updateProcessorHealth(processor, false, Integer.MAX_VALUE);
            }
        } catch (Exception e) {
            System.out.println("Erro inesperado, servico fora "+ e);
            processorSelector.updateProcessorHealth(processor, false, Integer.MAX_VALUE);
        }
    }

    private void parseAndUpdateHealth(String processor, String json) {
        try {
            int failingStart = json.indexOf("\"failing\":") + 10;
            boolean failing = Boolean.parseBoolean(json.substring(failingStart, json.indexOf(",", failingStart)).trim());

            int timeStart = json.indexOf("\"minResponseTime\":") + 18;
            int minResponseTime = Integer.parseInt(json.substring(timeStart, json.indexOf("}", timeStart)).trim());

            processorSelector.updateProcessorHealth(processor, !failing, minResponseTime);
        } catch (Exception e) {
            System.err.println("Error parsing health check: " + e.getMessage());
            processorSelector.updateProcessorHealth(processor, false, Integer.MAX_VALUE);
        }
    }
}
