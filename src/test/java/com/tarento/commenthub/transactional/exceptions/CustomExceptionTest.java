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

        ex.setCode("ERR001");
        ex.setMessage("Some error occurred");
        ex.setHttpStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        assertEquals("ERR001", ex.getCode());
        assertEquals("Some error occurred", ex.getMessage());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getHttpStatusCode());
    }

    @Test
    void testAllArgsConstructor() {
        CustomException ex = new CustomException("ERR002", "Another error", HttpStatus.BAD_REQUEST);

        assertEquals("ERR002", ex.getCode());
        assertEquals("Another error", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatusCode());
    }
}
