package com.tarento.commenthub.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.commenthub.constant.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisTemplate<String, String> redisDataTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ValueOperations<String, String> dataValueOperations;

    @InjectMocks
    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cacheService, "cacheTtl", 3600L);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisDataTemplate.opsForValue()).thenReturn(dataValueOperations);
    }

    @Test
    void testPutCache_Success() throws JsonProcessingException {
        String key = "testKey";
        Object object = "testObject";
        String jsonData = "\"testObject\"";

        when(objectMapper.writeValueAsString(object)).thenReturn(jsonData);

        cacheService.putCache(key, object);

        verify(objectMapper).writeValueAsString(object);
        verify(valueOperations).set(Constants.COMMENT_TREE_REDIS_KEY + key, jsonData, 3600L, TimeUnit.SECONDS);
    }

    @Test
    void testPutCache_JsonProcessingException() throws JsonProcessingException {
        String key = "testKey";
        Object object = "testObject";

        when(objectMapper.writeValueAsString(object)).thenThrow(new JsonProcessingException("JSON error") {});

        assertDoesNotThrow(() -> cacheService.putCache(key, object));

        verify(objectMapper).writeValueAsString(object);
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testPutCache_RuntimeException() throws JsonProcessingException {
        String key = "testKey";
        Object object = "testObject";

        when(objectMapper.writeValueAsString(object)).thenThrow(new RuntimeException("Runtime error"));

        assertDoesNotThrow(() -> cacheService.putCache(key, object));

        verify(objectMapper).writeValueAsString(object);
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void testGetCache_Success() {
        String key = "testKey";
        String expectedValue = "testValue";

        when(valueOperations.get(Constants.COMMENT_TREE_REDIS_KEY + key)).thenReturn(expectedValue);

        String result = cacheService.getCache(key);

        assertEquals(expectedValue, result);
        verify(valueOperations).get(Constants.COMMENT_TREE_REDIS_KEY + key);
    }

    @Test
    void testGetCache_Exception() {
        String key = "testKey";

        when(valueOperations.get(Constants.COMMENT_TREE_REDIS_KEY + key)).thenThrow(new RuntimeException("Redis error"));

        String result = cacheService.getCache(key);

        assertNull(result);
        verify(valueOperations).get(Constants.COMMENT_TREE_REDIS_KEY + key);
    }

    @Test
    void testDeleteCache_Success() {
        String key = "testKey";

        when(redisTemplate.delete(Constants.COMMENT_TREE_REDIS_KEY + key)).thenReturn(true);

        Long result = cacheService.deleteCache(key);

        assertNull(result);
        verify(redisTemplate).delete(Constants.COMMENT_TREE_REDIS_KEY + key);
    }

    @Test
    void testDeleteCache_NotFound() {
        String key = "testKey";

        when(redisTemplate.delete(Constants.COMMENT_TREE_REDIS_KEY + key)).thenReturn(false);

        Long result = cacheService.deleteCache(key);

        assertNull(result);
        verify(redisTemplate).delete(Constants.COMMENT_TREE_REDIS_KEY + key);
    }

    @Test
    void testDeleteCache_Exception() {
        String key = "testKey";

        when(redisTemplate.delete(Constants.COMMENT_TREE_REDIS_KEY + key)).thenThrow(new RuntimeException("Redis error"));

        Long result = cacheService.deleteCache(key);

        assertNull(result);
        verify(redisTemplate).delete(Constants.COMMENT_TREE_REDIS_KEY + key);
    }

    @Test
    void testHget_Success() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");
        
        when(dataValueOperations.get("key1")).thenReturn("value1");
        when(dataValueOperations.get("key2")).thenReturn("value2");
        when(dataValueOperations.get("key3")).thenReturn("value3");

        List<Object> result = cacheService.hget(keys);

        assertEquals(3, result.size());
        assertEquals("value1", result.get(0));
        assertEquals("value2", result.get(1));
        assertEquals("value3", result.get(2));
        
        verify(dataValueOperations).get("key1");
        verify(dataValueOperations).get("key2");
        verify(dataValueOperations).get("key3");
    }

    @Test
    void testHget_Exception() {
        List<String> keys = Arrays.asList("key1", "key2");
        
        when(dataValueOperations.get("key1")).thenThrow(new RuntimeException("Redis error"));

        List<Object> result = cacheService.hget(keys);

        assertTrue(result.isEmpty());
        verify(dataValueOperations).get("key1");
    }

    @Test
    void testHgetMulti_Success() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");
        List<String> values = Arrays.asList("value1", "value2", "value3");

        when(valueOperations.multiGet(keys)).thenReturn(values);

        List<Object> result = cacheService.hgetMulti(keys);

        assertEquals(3, result.size());
        assertEquals("value1", result.get(0));
        assertEquals("value2", result.get(1));
        assertEquals("value3", result.get(2));
        
        verify(valueOperations).multiGet(keys);
    }

    @Test
    void testHgetMulti_NullValues() {
        List<String> keys = Arrays.asList("key1", "key2");
        List<String> values = Arrays.asList(null, "value2");

        when(valueOperations.multiGet(keys)).thenReturn(values);

        List<Object> result = cacheService.hgetMulti(keys);

        assertEquals(2, result.size());
        assertNull(result.get(0));
        assertEquals("value2", result.get(1));
        
        verify(valueOperations).multiGet(keys);
    }
}