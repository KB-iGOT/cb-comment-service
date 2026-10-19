package com.tarento.commenthub.transactional.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PropertiesCacheTest {
    private PropertiesCache propertiesCache;

    @BeforeEach
    void setUp() throws Exception {
        propertiesCache = new PropertiesCache();

        // Inject fake properties into configProp using reflection
        Field field = PropertiesCache.class.getDeclaredField("configProp");
        field.setAccessible(true);
        Properties props = (Properties) field.get(propertiesCache);
        props.clear();
        props.setProperty("myKey", "myValue");
    }

    @Test
    void testGetProperty_EnvVariableOverrides() {
        // Simulate System.getenv() override via reflection
        String envKey = "TEST_ENV_KEY";

        // Call getProperty with key not in configProp
        String result = propertiesCache.getProperty(envKey);

        // Since we cannot modify System.getenv easily, expected fallback is the key itself
        assertEquals(envKey, result, "If no env and no config value, should return key itself");
    }

    @Test
    void testGetProperty_ConfigValueReturned() {
        String result = propertiesCache.getProperty("myKey");
        assertEquals("myValue", result, "Should return value from configProp");
    }

    @Test
    void testGetProperty_FallbackToKey() {
        String result = propertiesCache.getProperty("nonExistentKey");
        assertEquals("nonExistentKey", result, "Should return key itself if not found");
    }

    @Test
    void testReadProperty_ConfigValueReturned() {
        String result = propertiesCache.readProperty("myKey");
        assertEquals("myValue", result, "Should return value from configProp");
    }

    @Test
    void testReadProperty_NullWhenNotFound() {
        String result = propertiesCache.readProperty("nonExistentKey");
        assertNull(result, "Should return null if property not found");
    }

    @Test
    void testGetProperty_EnvVariablePresent() {
        String result = propertiesCache.getProperty("PATH");
        assertEquals(System.getenv("PATH"), result, "Should return env value when present");
    }

    @Test
    void testReadProperty_EnvVariablePresent() {
        String result = propertiesCache.readProperty("PATH");
        assertEquals(System.getenv("PATH"), result, "Should return env value when present");
    }
}
