package com.tarento.commenthub.utility;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DataCacheManagerTest {

    private DataCacheManager dataCacheManager;

    @BeforeEach
    void setUp() {
        dataCacheManager = new DataCacheManager();
    }

    @Test
    void testPutAndGetContentFromCache_whenKeyExists() {
        String key = "testKey";
        Map<String, Object> value = new HashMap<>();
        value.put("name", "sample");

        dataCacheManager.putContentInCache(key, value);
        Map<String, Object> result = dataCacheManager.getContentFromCache(key);

        assertNotNull(result);
        assertEquals("sample", result.get("name"));
    }

    @Test
    void testGetContentFromCache_whenKeyDoesNotExist() {
        String key = "nonExistingKey";
        Map<String, Object> result = dataCacheManager.getContentFromCache(key);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
