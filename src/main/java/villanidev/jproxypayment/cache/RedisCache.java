package villanidev.jproxypayment.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import villanidev.jproxypayment.dto.PaymentSummary;

import java.time.Instant;
import java.util.Set;

public class RedisCache {
    private static final String DEFAULT_TOTAL = "default:total";
    private static final String FALLBACK_TOTAL = "fallback:total";
    private static final String DEFAULT_AMOUNT = "default:amount";
    private static final String FALLBACK_AMOUNT = "fallback:amount";
    private static final String PAYMENTS_PREFIX = "payment:";

    private final JedisPool jedisPool;

    public RedisCache(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public void recordPayment(String processor, double amount, Instant timestamp) {
        try (Jedis jedis = jedisPool.getResource()) {
            // Incrementa contadores totais
            jedis.incr(processor + ":total");
            jedis.incrByFloat(processor + ":amount", amount);

            // Armazena detalhes para queries temporais
            String paymentKey = PAYMENTS_PREFIX + processor + ":" + timestamp.toEpochMilli();
            jedis.hset(paymentKey, "amount", String.valueOf(amount));
            jedis.expire(paymentKey, 86400); // Expira em 24h
        }
    }

    public PaymentSummary getSummary(Instant from, Instant to) {
        try (Jedis jedis = jedisPool.getResource()) {
            long defaultTotal = Long.parseLong(jedis.get(DEFAULT_TOTAL));
            long fallbackTotal = Long.parseLong(jedis.get(FALLBACK_TOTAL));
            double defaultAmount = Double.parseDouble(jedis.get(DEFAULT_AMOUNT));
            double fallbackAmount = Double.parseDouble(jedis.get(FALLBACK_AMOUNT));

            if (from != null && to != null) {
                defaultTotal = countPaymentsInRange(jedis, "default", from, to);
                fallbackTotal = countPaymentsInRange(jedis, "fallback", from, to);
                defaultAmount = sumAmountsInRange(jedis, "default", from, to);
                fallbackAmount = sumAmountsInRange(jedis, "fallback", from, to);
            }

            return new PaymentSummary(defaultTotal, defaultAmount, fallbackTotal, fallbackAmount);
        }
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
