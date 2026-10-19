package com.tarento.commenthub;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class CommentHubApplicationTest {

    @InjectMocks
    private CommentHubApplication application;

    @Test
    void testMain() {
        try (MockedStatic<SpringApplication> mockedStatic = Mockito.mockStatic(SpringApplication.class)) {
            CommentHubApplication.main(new String[]{"arg1", "arg2"});
            mockedStatic.verify(() -> SpringApplication.run(eq(CommentHubApplication.class), eq(new String[]{"arg1", "arg2"})));
        }
    }

    @Test
    void testRestTemplate() {
        RestTemplate restTemplate = application.restTemplate();
        assertNotNull(restTemplate);
    }
}