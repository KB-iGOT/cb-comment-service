package com.tarento.commenthub.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @InjectMocks
    private HealthController healthController;

    @Test
    void testLivenessCheck() throws Exception {
        ResponseEntity<?> response = healthController.livenessCheck();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Status ok", response.getBody());
    }
}
