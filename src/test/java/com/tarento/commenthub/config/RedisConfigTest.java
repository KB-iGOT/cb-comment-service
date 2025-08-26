package com.tarento.commenthub.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import com.tarento.commenthub.constant.Constants;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {
        "redis.ttl=5000",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "spring.redis.data.host=localhost",
        "spring.redis.data.port=6380"
})
@ContextConfiguration(classes = {RedisConfig.class, RedisConfigTest.CacheManagerTestConfig.class})
class RedisConfigTest {

    @TestConfiguration
    static class CacheManagerTestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Autowired
    private RedisConfig redisConfig;

    @Autowired
    @Qualifier(Constants.REDIS_CONNECTION_FACTORY)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired
    @Qualifier(Constants.REDIS_DATA_CONNECTION_FACTORY)
    private RedisConnectionFactory redisDataConnectionFactory;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    @Qualifier(Constants.REDIS_DATA_TEMPLATE)
    private RedisTemplate<String, String> redisDataTemplate;

    @Test
    void testRedisConnectionFactory() {
        RedisConnectionFactory factory = redisConfig.redisConnectionFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
    }

    @Test
    void testRedisDataConnectionFactory() {
        RedisConnectionFactory factory = redisConfig.redisDataConnectionFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof LettuceConnectionFactory);
    }

    @Test
    void testRedisTemplate() {
        RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);
        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
        assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getValueSerializer() instanceof StringRedisSerializer);
    }

    @Test
    void testRedisDataTemplate() {
        RedisTemplate<String, String> template = redisConfig.redisDataTemplate(redisDataConnectionFactory);
        assertNotNull(template);
        assertNotNull(template.getConnectionFactory());
        assertTrue(template.getKeySerializer() instanceof StringRedisSerializer);
        assertTrue(template.getValueSerializer() instanceof StringRedisSerializer);
    }

    @Test
    void testInjectedBeans() {
        assertNotNull(redisConnectionFactory);
        assertNotNull(redisDataConnectionFactory);
        assertNotNull(redisTemplate);
        assertNotNull(redisDataTemplate);
    }

}