package com.tarento.commenthub.exception;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ErrorResponseTest {
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

    @Test
    void testBuilderWithNulls_defaultsHandled() {
        ErrorResponse response = ErrorResponse.builder().build();

        assertNull(response.getCode());
        assertNull(response.getMessage());
        assertNull(response.getErrors());
        assertNull(response.getHttpStatusCode());
    }

    @Test
    void testEquals_selfAndNullAndDifferentType() {
        ErrorResponse response = ErrorResponse.builder()
                .code("X1")
                .message("msg")
                .errors(Map.of())
                .httpStatusCode(100)
                .build();

        // self comparison (should be true)
        assertEquals(response, response);

        // null comparison (should be false)
        assertNotEquals(null,response);

        // different type comparison (should be false)
        assertNotEquals( "some string",response);
    }

    @Test
    void testEquals_differentFieldValues() {
        ErrorResponse base = ErrorResponse.builder()
                .code("A")
                .message("msg")
                .errors(Map.of("f", "e"))
                .httpStatusCode(200)
                .build();

        // Different code
        ErrorResponse diffCode = ErrorResponse.builder()
                .code("B")
                .message("msg")
                .errors(Map.of("f", "e"))
                .httpStatusCode(200)
                .build();
        assertNotEquals(base, diffCode);

        // Different message
        ErrorResponse diffMsg = ErrorResponse.builder()
                .code("A")
                .message("different")
                .errors(Map.of("f", "e"))
                .httpStatusCode(200)
                .build();
        assertNotEquals(base, diffMsg);

        // Different errors
        ErrorResponse diffErrors = ErrorResponse.builder()
                .code("A")
                .message("msg")
                .errors(Map.of("other", "x"))
                .httpStatusCode(200)
                .build();
        assertNotEquals(base, diffErrors);

        // Different httpStatusCode
        ErrorResponse diffStatus = ErrorResponse.builder()
                .code("A")
                .message("msg")
                .errors(Map.of("f", "e"))
                .httpStatusCode(500)
                .build();
        assertNotEquals(base, diffStatus);
    }

    @Test
    void testHashCode_consistency() {
        ErrorResponse response = ErrorResponse.builder()
                .code("HASH")
                .message("Check hash")
                .errors(Map.of("k", "v"))
                .httpStatusCode(123)
                .build();

        int hash1 = response.hashCode();
        int hash2 = response.hashCode();

        assertEquals(hash1, hash2); // hashCode must be consistent
    }

    @Test
    void testEquals_differentCodeOnly() {
        ErrorResponse r1 = ErrorResponse.builder()
                .code("A").message("msg").errors(Map.of("f","e")).httpStatusCode(200).build();
        ErrorResponse r2 = ErrorResponse.builder()
                .code("B").message("msg").errors(Map.of("f","e")).httpStatusCode(200).build();
        assertNotEquals(r1, r2);  // covers code mismatch
    }

    @Test
    void testEquals_differentMessageOnly() {
        ErrorResponse r1 = ErrorResponse.builder()
                .code("A").message("msg").errors(Map.of("f","e")).httpStatusCode(200).build();
        ErrorResponse r2 = ErrorResponse.builder()
                .code("A").message("other").errors(Map.of("f","e")).httpStatusCode(200).build();
        assertNotEquals(r1, r2);  // covers message mismatch
    }

    @Test
    void testEquals_differentErrorsOnly() {
        ErrorResponse r1 = ErrorResponse.builder()
                .code("A").message("msg").errors(Map.of("f","e")).httpStatusCode(200).build();
        ErrorResponse r2 = ErrorResponse.builder()
                .code("A").message("msg").errors(Map.of("x","y")).httpStatusCode(200).build();
        assertNotEquals(r1, r2);  // covers errors mismatch
    }

    @Test
    void testEquals_differentHttpStatusOnly() {
        ErrorResponse r1 = ErrorResponse.builder()
                .code("A").message("msg").errors(Map.of("f","e")).httpStatusCode(200).build();
        ErrorResponse r2 = ErrorResponse.builder()
                .code("A").message("msg").errors(Map.of("f","e")).httpStatusCode(500).build();
        assertNotEquals(r1, r2);  // covers httpStatus mismatch
    }



    @Test
    void testEquals_withNullFields() {
        ErrorResponse r1 = ErrorResponse.builder().build(); // all null
        ErrorResponse r2 = ErrorResponse.builder().build();

        assertEquals(r1, r2); // both null → equal

        ErrorResponse r3 = ErrorResponse.builder().code("X").build();
        assertNotEquals(r1, r3); // null vs non-null field
    }

    @Test
    void testToString_withNulls() {
        ErrorResponse response = ErrorResponse.builder().build();
        String str = response.toString();
        assertNotNull(str);
        assertTrue(str.contains("null")); // verifies nulls are represented
    }


    @Test
    void testEqualsWithNullFields() {
        ErrorResponse r1 = ErrorResponse.builder().build(); // all null
        ErrorResponse r2 = ErrorResponse.builder().build(); // all null
        ErrorResponse r3 = ErrorResponse.builder().code("X").build();

        assertEquals(r1, r2);     // both empty → equal
        assertNotEquals(r1, r3);  // null vs non-null
    }

    @Test
    void testHashCodeWithNullFields() {
        ErrorResponse r1 = ErrorResponse.builder().build();
        ErrorResponse r2 = ErrorResponse.builder().build();

        assertEquals(r1.hashCode(), r2.hashCode()); // consistent even with nulls
    }
}
