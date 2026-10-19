package com.tarento.commenthub.transactional.cassandrautils;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class CassandraPropertyReaderTest {

    @Test
    void testReadPropertyReturnsValueIfPresent() throws Exception {
        CassandraPropertyReader reader = new CassandraPropertyReader();

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
        CassandraPropertyReader reader = new CassandraPropertyReader();
        assertEquals("unknownKey", reader.readProperty("unknownKey"));
    }

}
