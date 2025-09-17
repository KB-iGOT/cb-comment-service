package com.tarento.commenthub.transactional.cassandrautils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class CassandraPropertyReaderTest {
    @Test
    void testSingletonReturnsSameInstance() {
        CassandraPropertyReader instance1 = CassandraPropertyReader.getInstance();
        CassandraPropertyReader instance2 = CassandraPropertyReader.getInstance();

        assertNotNull(instance1);
        assertSame(instance1, instance2, "getInstance should always return same object");
    }

    @Test
    void testReadPropertyReturnsValueIfPresent() throws Exception {
        CassandraPropertyReader reader = CassandraPropertyReader.getInstance();

        // Inject custom properties via reflection
        Field propsField = CassandraPropertyReader.class.getDeclaredField("properties");
        propsField.setAccessible(true);
        Properties props = new Properties();
        props.setProperty("myKey", "myValue");
        propsField.set(reader, props);

        assertEquals("myValue", reader.readProperty("myKey"));
    }

    @Test
    void testReadPropertyReturnsKeyIfNotFound() {
        CassandraPropertyReader reader = CassandraPropertyReader.getInstance();
        assertEquals("unknownKey", reader.readProperty("unknownKey"));
    }

}
