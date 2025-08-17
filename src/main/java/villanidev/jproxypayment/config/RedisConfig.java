package villanidev.jproxypayment.config;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisConfig {
    private final JedisPool jedisPool;

    public RedisConfig(String redisHost) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        this.jedisPool = new JedisPool(poolConfig, redisHost);
    }

    public JedisPool getJedisPool() {
        return jedisPool;
    }
}