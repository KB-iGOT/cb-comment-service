package com.tarento.commenthub.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ErrorResponseTest {
    @Test
    void testBuilderAndGetters() {
        Map<String, String> errors = new HashMap<>();
        errors.put("field1", "must not be null");

        ErrorResponse response = ErrorResponse.builder()
                .code("ERR001")
                .message("Validation failed")
                .errors(errors)
                .httpStatusCode(400)
                .build();

        assertEquals("ERR001", response.getCode());
        assertEquals("Validation failed", response.getMessage());
        assertEquals(errors, response.getErrors());
        assertEquals(400, response.getHttpStatusCode());
    }

    @Test
    void testImmutability() {
        Map<String, String> errors = new HashMap<>();
        errors.put("field", "error");

        ErrorResponse response = ErrorResponse.builder()
                .code("IMMUTABLE")
                .message("Check immutability")
                .errors(errors)
                .httpStatusCode(500)
                .build();

        // Ensure getters return expected values
        assertEquals("IMMUTABLE", response.getCode());
        assertEquals("Check immutability", response.getMessage());

        // Lombok @Value makes the class final & fields private final
        // There are no setters, so immutability is guaranteed
        assertThrows(NoSuchMethodException.class,
                () -> ErrorResponse.class.getDeclaredMethod("setCode", String.class));
    }

    @Test
    void testEqualsAndHashCodeAndToString() {
        Map<String, String> errors = Map.of("f", "e");

        ErrorResponse response1 = ErrorResponse.builder()
                .code("E1")
                .message("msg")
                .errors(errors)
                .httpStatusCode(200)
                .build();

        ErrorResponse response2 = ErrorResponse.builder()
                .code("E1")
                .message("msg")
                .errors(errors)
                .httpStatusCode(200)
                .build();

        ErrorResponse response3 = ErrorResponse.builder()
                .code("E2")
                .message("different")
                .errors(Map.of())
                .httpStatusCode(404)
                .build();

        // Lombok @Value implements equals/hashCode based on fields
        assertEquals(response1, response2);
        assertEquals(response1.hashCode(), response2.hashCode());

        assertNotEquals(response1, response3);

        assertNotNull(response1.toString());
        assertTrue(response1.toString().contains("E1"));
    }
}
