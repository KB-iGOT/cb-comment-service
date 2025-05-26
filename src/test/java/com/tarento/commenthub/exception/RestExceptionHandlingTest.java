package com.tarento.commenthub.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class RestExceptionHandlingTest {

    private RestExceptionHandling restExceptionHandling;

    @BeforeEach
    void setUp() {
        restExceptionHandling = new RestExceptionHandling();
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new Exception("Something went wrong");

        ResponseEntity<?> response = restExceptionHandling.handleException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());

        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertEquals("Something went wrong", errorResponse.getCode());
    }

    @Test
    void testHandleCommentExceptionWithHttpStatus() {
        CommentException commentException = new CommentException("ERR_CODE", "Custom message", 400);

        ResponseEntity<?> response = restExceptionHandling.handleException(commentException);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertEquals("Custom message", errorResponse.getMessage());
        assertEquals(400, errorResponse.getHttpStatusCode());
    }

    @Test
    void testHandleCommentExceptionWithNullHttpStatus() {
        CommentException commentException = new CommentException("ERR_NO_STATUS", "Fallback to OK", null);

        ResponseEntity<?> response = restExceptionHandling.handleException(commentException);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ErrorResponse errorResponse = (ErrorResponse) response.getBody();
        assertEquals("Fallback to OK", errorResponse.getMessage());
        assertEquals(200, errorResponse.getHttpStatusCode());  // fallback
    }
}
