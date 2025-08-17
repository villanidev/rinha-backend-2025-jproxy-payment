package villanidev.jproxypayment.service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

public class DistributedProcessorSelector {

    record ProcessorHealth(boolean isFailing, int responseTime) {}
    private static final String REDIS_KEY = "best_processor";
    private static final String HEALTH_KEY_PREFIX = "processor_health:";
    private static final Duration HEALTH_TTL = Duration.ofSeconds(4);

    private final JedisPool jedisPool;
    private final AtomicReference<String> cachedBestProcessor = new AtomicReference<>("default");
    private final String instanceId;
    private volatile long lastCacheUpdate = 0;

    public DistributedProcessorSelector(JedisPool jedisPool, String instanceId) {
        this.jedisPool = jedisPool;
        this.instanceId = instanceId;
    }

    public String selectBestProcessor() {
        if (shouldRefreshCache()) {
            refreshCache();
        }
        return cachedBestProcessor.get();
    }

    public void updateProcessorHealth(String processor, boolean isHealthy, int responseTime) {
        try (Jedis jedis = jedisPool.getResource()) {
            String healthKey = HEALTH_KEY_PREFIX + processor;
            String healthValue = String.format("%b:%d:%d",
                    isHealthy,
                    responseTime,
                    Instant.now().getEpochSecond());

            jedis.setex(healthKey, HEALTH_TTL.getSeconds(), healthValue);

            tryAcquireLockAndElectBestProcessor(jedis);
        }
    }

    private boolean shouldRefreshCache() {
        return System.currentTimeMillis() - lastCacheUpdate > 1000; // 1 segundo
    }

    private void refreshCache() {
        try (Jedis jedis = jedisPool.getResource()) {
            String currentBest = jedis.get(REDIS_KEY);
            if (currentBest != null) {
                cachedBestProcessor.set(currentBest);
                lastCacheUpdate = System.currentTimeMillis();
            }
        }
    }

    private void tryAcquireLockAndElectBestProcessor(Jedis jedis) {
        String lockKey = REDIS_KEY + ":lock";
        String lockValue = instanceId + ":" + System.currentTimeMillis();

        try {
            if (jedis.setnx(lockKey, lockValue) == 1) {
                jedis.expire(lockKey, 1);

                String bestProcessor = electBestProcessor(jedis);
                jedis.setex(REDIS_KEY, HEALTH_TTL.getSeconds(), bestProcessor);
                cachedBestProcessor.set(bestProcessor);
                System.out.println("Setting best processor: "+ bestProcessor);
                lastCacheUpdate = System.currentTimeMillis();
            }
        } finally {
            if (lockValue.equals(jedis.get(lockKey))) {
                jedis.del(lockKey);
            }
        }
    }

    private String electBestProcessor(Jedis jedis) {
        ProcessorHealth defaultHealth = getProcessorHealth(jedis, "default");
        ProcessorHealth fallbackHealth = getProcessorHealth(jedis, "fallback");

        if (!defaultHealth.isFailing() && !fallbackHealth.isFailing()) {
            return defaultHealth.responseTime() <= fallbackHealth.responseTime() ?
                    "default" : "fallback";
        } else if (!defaultHealth.isFailing()) {
            return "default";
        } else if (!fallbackHealth.isFailing()) {
            return "fallback";
        } else {
            return defaultHealth.responseTime() <= fallbackHealth.responseTime() ?
                    "default" : "fallback";
        }
    }

    private ProcessorHealth getProcessorHealth(Jedis jedis, String processor) {
        String healthValue = jedis.get(HEALTH_KEY_PREFIX + processor);
        if (healthValue == null) {
            return new ProcessorHealth(true, Integer.MAX_VALUE);
        }

        String[] parts = healthValue.split(":");
        boolean isFailing = !Boolean.parseBoolean(parts[0]);
        int responseTime = Integer.parseInt(parts[1]);
        long timestamp = Long.parseLong(parts[2]);

        if (Instant.now().minus(HEALTH_TTL).isAfter(Instant.ofEpochSecond(timestamp))) {
            return new ProcessorHealth(true, Integer.MAX_VALUE);
        }

        return new ProcessorHealth(isFailing, responseTime);
    }
}
