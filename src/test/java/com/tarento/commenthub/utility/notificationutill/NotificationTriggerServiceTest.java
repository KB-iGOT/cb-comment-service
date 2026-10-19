package com.tarento.commenthub.utility.notificationutill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.utility.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationTriggerServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CbServerProperties serverConfig;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationTriggerService notificationTriggerService;

    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        realObjectMapper = new ObjectMapper();
    }

    @Test
    void testSendNotification_Success() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1", "user2");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test Title");

        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");

        ResponseEntity<Map<String, Object>> successResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(successResponse);

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @ParameterizedTest
    @CsvSource({
        ", COMMENT",
        "'', COMMENT",
        "'   ', COMMENT",
        "ENGAGEMENT, ",
        "ENGAGEMENT, ''",
        "ENGAGEMENT, '   '"
    })
    void testSendNotification_InvalidSubCategoryOrSubType(String subCategory, String subType) {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_NullUserIds() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = null;
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_EmptyUserIds() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Collections.emptyList();
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_NullMessage() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = null;

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_EmptyMessage() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_HttpClientErrorException() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request", "Error response".getBytes(), null);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(exception);

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_GenericException() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testSendNotification_NonSuccessfulResponse() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        ResponseEntity<Map<String, Object>> errorResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(errorResponse);

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testTriggerNotification_Success() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1", "user2");
        String userName = "John Doe";
        String title = "Test Course";
        Map<String, Object> data = new HashMap<>();
        data.put("courseId", "course123");

        ObjectNode mockPlaceholders = realObjectMapper.createObjectNode();
        mockPlaceholders.put(Constants.TITLE, title);
        mockPlaceholders.put(Constants.USER_NAME, userName);

        when(objectMapper.createObjectNode()).thenReturn(mockPlaceholders);
        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        ResponseEntity<Map<String, Object>> successResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(successResponse);

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testTriggerNotification_SendNotificationThrowsException() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        String userName = "John Doe";
        String title = "Test Course";
        Map<String, Object> data = new HashMap<>();

        ObjectNode mockPlaceholders = realObjectMapper.createObjectNode();
        mockPlaceholders.put(Constants.TITLE, title);
        mockPlaceholders.put(Constants.USER_NAME, userName);

        when(objectMapper.createObjectNode()).thenReturn(mockPlaceholders);
        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testTriggerNotification_CatchesExceptionFromSendNotification() {
        NotificationTriggerService spyService =
                spy(new NotificationTriggerService(restTemplate, serverConfig, objectMapper));

        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        String userName = "John Doe";
        String title = "Test Course";
        Map<String, Object> data = new HashMap<>();

        ObjectNode mockPlaceholders = realObjectMapper.createObjectNode();
        mockPlaceholders.put(Constants.TITLE, title);
        mockPlaceholders.put(Constants.USER_NAME, userName);

        when(objectMapper.createObjectNode()).thenReturn(mockPlaceholders);
        doThrow(new RuntimeException("unexpected failure"))
                .when(spyService).sendNotification(eq(subCategory), eq(subType), eq(userIds), anyMap());

        assertDoesNotThrow(() -> spyService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(spyService).sendNotification(eq(subCategory), eq(subType), eq(userIds), anyMap());
    }

    @Test
    void testTriggerNotification_WithNullValues() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        String userName = null;
        String title = null;
        Map<String, Object> data = null;

        ObjectNode mockPlaceholders = realObjectMapper.createObjectNode();
        mockPlaceholders.put(Constants.TITLE, (String) null);
        mockPlaceholders.put(Constants.USER_NAME, (String) null);

        when(objectMapper.createObjectNode()).thenReturn(mockPlaceholders);

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }

    @Test
    void testTriggerNotification_WithEmptyData() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        String userName = "John";
        String title = "Course";
        Map<String, Object> data = new HashMap<>();

        ObjectNode mockPlaceholders = realObjectMapper.createObjectNode();
        mockPlaceholders.put(Constants.TITLE, title);
        mockPlaceholders.put(Constants.USER_NAME, userName);

        when(objectMapper.createObjectNode()).thenReturn(mockPlaceholders);
        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        ResponseEntity<Map<String, Object>> successResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class)))
                .thenReturn(successResponse);

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate).exchange(eq("http://notification-api.com"), eq(HttpMethod.POST), any(HttpEntity.class), any(ParameterizedTypeReference.class));
    }
}