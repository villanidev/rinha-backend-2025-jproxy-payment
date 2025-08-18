package villanidev.jproxypayment.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import villanidev.jproxypayment.dto.PaymentSummary;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public class RedisCacheClient {
    private static final String DEFAULT_TOTAL = "default:total";
    private static final String FALLBACK_TOTAL = "fallback:total";
    private static final String DEFAULT_AMOUNT = "default:amount";
    private static final String FALLBACK_AMOUNT = "fallback:amount";
    private static final String PAYMENTS_PREFIX = "payment:";

    private final JedisPool jedisPool;

    public RedisCacheClient(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public void savePayment(String processor, double amount, Instant timestamp) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Incrementa contadores totais
            jedis.incr(processor + ":total");
            jedis.incrByFloat(processor + ":amount", amount);

            // Armazena detalhes para queries temporais
            String paymentKey = PAYMENTS_PREFIX + processor + ":" + timestamp.toEpochMilli();
            jedis.hset(paymentKey, "amount", String.valueOf(amount));
            jedis.expire(paymentKey, 86400); // Expira em 24h

            System.out.println(Thread.currentThread().getName() +"- Saved payment: " + processor + ", " + amount + ", " + timestamp);
        }
    }

    public PaymentSummary getSummary(Instant from, Instant to) {
        try (Jedis jedis = jedisPool.getResource()) {
            long defaultTotal = Long.parseLong(getWithDefault(jedis,DEFAULT_TOTAL, "0"));
            long fallbackTotal = Long.parseLong(getWithDefault(jedis,FALLBACK_TOTAL, "0"));
            double defaultAmount = Double.parseDouble(getWithDefault(jedis,DEFAULT_AMOUNT, "0"));
            double fallbackAmount = Double.parseDouble(getWithDefault(jedis,FALLBACK_AMOUNT, "0"));

            if (from != null && to != null) {
                defaultTotal = countPaymentsInRange(jedis, "default", from, to);
                fallbackTotal = countPaymentsInRange(jedis, "fallback", from, to);
                defaultAmount = sumAmountsInRange(jedis, "default", from, to);
                fallbackAmount = sumAmountsInRange(jedis, "fallback", from, to);
            }

            return new PaymentSummary(defaultTotal, defaultAmount, fallbackTotal, fallbackAmount);
        }
    }

    private String getWithDefault(Jedis jedis, String cacheKey, String defaultValue) {
        return Optional.ofNullable(jedis.get(cacheKey)).orElse(defaultValue);
    }

    private long countPaymentsInRange(Jedis jedis, String processor, Instant from, Instant to) {
        Set<String> keys = jedis.keys(PAYMENTS_PREFIX + processor + ":*");
        return keys.stream()
                .filter(key -> {
                    long timestamp = Long.parseLong(key.split(":")[2]);
                    return timestamp >= from.toEpochMilli() && timestamp <= to.toEpochMilli();
                })
                .count();
    }

    private double sumAmountsInRange(Jedis jedis, String processor, Instant from, Instant to) {
        Set<String> keys = jedis.keys(PAYMENTS_PREFIX + processor + ":*");
        return keys.stream()
                .filter(key -> {
                    long timestamp = Long.parseLong(key.split(":")[2]);
                    return timestamp >= from.toEpochMilli() && timestamp <= to.toEpochMilli();
                })
                .mapToDouble(key -> Double.parseDouble(jedis.hget(key, "amount")))
                .sum();
    }
}
