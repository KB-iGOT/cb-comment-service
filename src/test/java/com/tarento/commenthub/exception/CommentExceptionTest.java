package com.tarento.commenthub.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CommentExceptionTest {

    @Test
    void testDefaultConstructor() {
        CommentException exception = new CommentException();
        assertNull(exception.getCode());
        assertNull(exception.getMessage());
        assertNull(exception.getHttpStatusCode());
        assertNull(exception.getErrors());
    }

    @Test
    void testConstructorWithCodeAndMessage() {
        CommentException exception = new CommentException("ERR001", "Something went wrong");

        assertEquals("ERR001", exception.getCode());
        assertEquals("Something went wrong", exception.getMessage());
        assertNull(exception.getHttpStatusCode());
        assertNull(exception.getErrors());
    }

    @Test
    void testConstructorWithCodeMessageAndHttpStatus() {
        CommentException exception = new CommentException("ERR002", "Invalid request", 400);

        assertEquals("ERR002", exception.getCode());
        assertEquals("Invalid request", exception.getMessage());
        assertEquals(400, exception.getHttpStatusCode());
        assertNull(exception.getErrors());
    }

    @Test
    void testConstructorWithErrorsMap() {
        Map<String, String> errors = new HashMap<>();
        errors.put("field1", "Field1 is required");
        errors.put("field2", "Field2 must be valid");

        CommentException exception = new CommentException(errors);

        assertEquals(errors, exception.getErrors());
        assertEquals(errors.toString(), exception.getMessage());
        assertNull(exception.getCode());
        assertNull(exception.getHttpStatusCode());
    }
}

