package com.tarento.commenthub.utility;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

class RedisCacheMngrTest {

    @InjectMocks
    private RedisCacheMngr redisCacheMngr;

    @Mock
    private JedisPool jedisPool;

    @Mock
    private Jedis jedis;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetContentFromCache_success() {
        String key = "sampleKey";
        String expectedValue = "cachedContent";

        when(jedisPool.getResource()).thenReturn(jedis);
        when(jedis.get(key)).thenReturn(expectedValue);

        String result = redisCacheMngr.getContentFromCache(key);

        assertEquals(expectedValue, result);
        verify(jedis).close(); // Ensure resource is closed
    }

    @Test
    void testGetContentFromCache_exception() {
        String key = "sampleKey";

        when(jedisPool.getResource()).thenThrow(new RuntimeException("Redis down"));

        String result = redisCacheMngr.getContentFromCache(key);

        assertNull(result);
    }
}
