package com.tarento.commenthub;

import static org.mockito.ArgumentMatchers.eq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.SpringApplication;

@ExtendWith(MockitoExtension.class)
class CommentHubApplicationTest {

    @InjectMocks
    private CommentHubApplication application;

    @Test
    void testMain() {
        // Testing the main method using MockedStatic
        try (MockedStatic<SpringApplication> mockedStatic = Mockito.mockStatic(SpringApplication.class)) {
            // Arrange and Act
            CommentHubApplication.main(new String[]{"arg1", "arg2"});

            // Assert
            mockedStatic.verify(() -> SpringApplication.run(eq(CommentHubApplication.class), eq(new String[]{"arg1", "arg2"})));
        }
    }
}