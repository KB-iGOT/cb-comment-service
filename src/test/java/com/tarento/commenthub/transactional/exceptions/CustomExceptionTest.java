package com.tarento.commenthub.transactional.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;

class CustomExceptionTest {

    @Test
    void testNoArgsConstructor() {
        CustomException ex = new CustomException();
        assertNull(ex.getCode());
        assertNull(ex.getMessage());
        assertNull(ex.getHttpStatusCode());
    }

    @Test
    void testAllArgsConstructor() {
        CustomException ex = new CustomException("ERR002", "Another error", HttpStatus.BAD_REQUEST);

        assertEquals("ERR002", ex.getCode());
        assertEquals("Another error", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatusCode());
    }

    @Test
    void testCassandraPropertyReaderException_setsMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        CassandraPropertyReaderException exception =
                new CassandraPropertyReaderException("Error loading properties", cause);

        assertEquals("Error loading properties", exception.getMessage());
        assertEquals(cause, exception.getCause());
    }
}
