package com.tarento.commenthub.utility.notificationutill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.utility.CbServerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Stream;

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
        
        ResponseEntity<Map> successResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(successResponse);

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
    }

    @ParameterizedTest(name = "subCategory=\"{0}\", subType=\"{1}\"")
    @MethodSource("invalidSubCategoryOrSubTypeProvider")
    void testSendNotification_InvalidSubCategoryOrSubType(String subCategory, String subType) {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    private static Stream<Arguments> invalidSubCategoryOrSubTypeProvider() {
        return Stream.of(
                Arguments.of(null, "COMMENT"),
                Arguments.of("", "COMMENT"),
                Arguments.of("   ", "COMMENT"),
                Arguments.of("ENGAGEMENT", null),
                Arguments.of("ENGAGEMENT", ""),
                Arguments.of("ENGAGEMENT", "   ")
        );
    }

    @Test
    void testSendNotification_NullUserIds() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = null;
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testSendNotification_EmptyUserIds() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Collections.emptyList();
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testSendNotification_NullMessage() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = null;

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testSendNotification_EmptyMessage() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
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
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(exception);

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testSendNotification_GenericException() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Unexpected error"));

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
    }

    @Test
    void testSendNotification_NonSuccessfulResponse() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> message = new HashMap<>();
        message.put("title", "Test");

        when(serverConfig.getNotificationApiUrl()).thenReturn("http://notification-api.com");
        
        ResponseEntity<Map> errorResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(errorResponse);

        assertDoesNotThrow(() -> notificationTriggerService.sendNotification(subCategory, subType, userIds, message));

        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
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
        
        ResponseEntity<Map> successResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(successResponse);

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
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
        
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
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
        verify(restTemplate, never()).postForEntity(anyString(), any(HttpEntity.class), eq(Map.class));
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
        
        ResponseEntity<Map> successResponse = new ResponseEntity<>(new HashMap<>(), HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(successResponse);

        assertDoesNotThrow(() -> notificationTriggerService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(objectMapper).createObjectNode();
        verify(restTemplate).postForEntity(eq("http://notification-api.com"), any(HttpEntity.class), eq(Map.class));
    }

    // triggerNotification's own try/catch only wraps the call to sendNotification(...), but
    // sendNotification internally catches IllegalArgumentException, HttpClientErrorException and
    // a generic Exception itself and never rethrows - so testTriggerNotification_SendNotificationThrowsException
    // (above) never actually reaches triggerNotification's catch block, since the exception thrown
    // by restTemplate is swallowed one level down. That leaves the "log.error(...)" line inside
    // triggerNotification's catch genuinely unreachable through the public API - the only way to
    // exercise it is to spy on the service and force sendNotification itself to throw.
    @Test
    void testTriggerNotification_CatchBlock_whenSendNotificationThrows() {
        String subCategory = "ENGAGEMENT";
        String subType = "COMMENT";
        List<String> userIds = Arrays.asList("user1");
        String userName = "John";
        String title = "Course";
        Map<String, Object> data = new HashMap<>();

        ObjectNode mockPlaceholders = realObjectMapper.createObjectNode();
        when(objectMapper.createObjectNode()).thenReturn(mockPlaceholders);

        NotificationTriggerService spyService = spy(notificationTriggerService);
        doThrow(new RuntimeException("forced failure"))
                .when(spyService).sendNotification(anyString(), anyString(), anyList(), anyMap());

        assertDoesNotThrow(() ->
                spyService.triggerNotification(subCategory, subType, userIds, userName, title, data));

        verify(spyService).sendNotification(eq(subCategory), eq(subType), eq(userIds), anyMap());
    }
}