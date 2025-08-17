package villanidev;

import com.sun.net.httpserver.HttpServer;
import villanidev.jproxypayment.cache.RedisCache;
import villanidev.jproxypayment.config.RedisConfig;
import villanidev.jproxypayment.handler.PaymentHandler;
import villanidev.jproxypayment.handler.SummaryHandler;
import villanidev.jproxypayment.service.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

public class Application {
    public static void main(String[] args) throws IOException {
        try {
            Instant start = Instant.now();
            int port = 8080;
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // Configuração do Redis
            String redisHost = System.getenv("REDIS_HOST");
            RedisConfig redisConfig = new RedisConfig(redisHost);
            RedisCache redisCache = new RedisCache(redisConfig.getJedisPool());

            // Processadores de pagamento
            String defaultProcessorUrl = System.getenv("DEFAULT_PROCESSOR_URL");
            String fallbackProcessorUrl = System.getenv("FALLBACK_PROCESSOR_URL");

            DefaultPaymentProcessor defaultProcessor = new DefaultPaymentProcessor(
                    defaultProcessorUrl);
            FallbackPaymentProcessor fallbackProcessor = new FallbackPaymentProcessor(
                    fallbackProcessorUrl);

            // Seleção distribuída de processadores
            DistributedProcessorSelector processorSelector = new DistributedProcessorSelector(
                    redisConfig.getJedisPool(),
                    System.getenv("INSTANCE_ID"));

            // Serviços
            PaymentQueueService queueService = new PaymentQueueService(
                    processorSelector,
                    redisCache,
                    defaultProcessor,
                    fallbackProcessor);

            // Agendador de health checks
            HealthCheckScheduler healthCheckScheduler = new HealthCheckScheduler(
                    processorSelector,
                    defaultProcessorUrl,
                    fallbackProcessorUrl);
            healthCheckScheduler.start();

            // Handlers
            server.createContext("/payments", new PaymentHandler(queueService));
            server.createContext("/payments-summary", new SummaryHandler(redisCache));

            // Configura a main thread virtual
            server.setExecutor(Executors.newFixedThreadPool(
                    20,
                    Thread.ofVirtual().name("mainVthread-", 0L).factory()
            ));

            server.start();

            Instant finish = Instant.now();
            long timeElapsed = Duration.between(start, finish).toMillis();
            System.out.println("Server started on port: " + port + " in " + timeElapsed + " (ms)");

        } catch (Exception e) {
            throw new RuntimeException("App initialization error: ", e);
        }
    }
}
