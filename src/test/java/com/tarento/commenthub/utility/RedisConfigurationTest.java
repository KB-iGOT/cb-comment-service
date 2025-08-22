package com.tarento.commenthub.utility;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Field;

class RedisConfigurationTest {

    @Test
    void testJedisPoolCreation() throws Exception {
        RedisConfiguration redisConfig = new RedisConfiguration();

        // Inject private fields using reflection
        Field hostField = RedisConfiguration.class.getDeclaredField("redisHost");
        Field portField = RedisConfiguration.class.getDeclaredField("redisPort");

        hostField.setAccessible(true);
        portField.setAccessible(true);

        hostField.set(redisConfig, "localhost");
        portField.set(redisConfig, 6379);

        JedisPool jedisPool = redisConfig.jedisPool();
        assertNotNull(jedisPool);
    }
}