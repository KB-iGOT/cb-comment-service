package com.tarento.commenthub.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


import static org.mockito.ArgumentMatchers.eq;

import com.tarento.commenthub.constant.Constants;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import com.tarento.commenthub.utility.DataCacheManager;
import com.tarento.commenthub.utility.CbServerProperties;
import com.tarento.commenthub.utility.RedisCacheMngr;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ContentServiceImplTest {
    @Mock
    private CbServerProperties serverConfig;
    @Mock
    private DataCacheManager dataCacheMgr;
    @Mock
    private RedisCacheMngr redisCacheMgr;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private ObjectMapper mapper;
    @Spy
    @InjectMocks
    private ContentServiceImpl contentService;
    private static final String CONTENT_ID = "test-content-id";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(contentService, "restTemplate", restTemplate);
        lenient().when(dataCacheMgr.getContentFromCache(anyString())).thenReturn(null);
        lenient().when(redisCacheMgr.getContentFromCache(anyString())).thenReturn(null);
    }

    @Test
    void testReadContentFromCache_WhenFieldsEmpty() {
        String contentId = "testContentId";
        List<String> fields = new ArrayList<>();
        when(serverConfig.getDefaultContentProperties()).thenReturn("field1");
        Map<String, Object> cachedData = new HashMap<>();
        cachedData.put("field1", "value1");
        when(dataCacheMgr.getContentFromCache(contentId)).thenReturn(cachedData);
        Map<String, Object> result = contentService.readContentFromCache(contentId, fields);
        assertEquals(cachedData, result);
    }

    @Test
    void testReadContentFromCache_WhenDataCacheHasData() {
        List<String> fields = List.of("field1", "field2");
        Map<String, Object> cachedData = new HashMap<>();
        cachedData.put("field1", "value1");
        cachedData.put("field2", "value2");
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(cachedData);
        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);
        assertEquals(cachedData, result);
    }

    @Test
    void testReadContentFromCache_WhenRedisHasData() throws Exception {
        List<String> fields = List.of("field1", "field2");
        String redisContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";
        Map<String, Object> contentData = new HashMap<>();
        contentData.put("field1", "value1");
        contentData.put("field2", "value2");
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        when(redisCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(redisContent);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenReturn(contentData);
        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);
        assertEquals(2, result.size());
        assertEquals("value1", result.get("field1"));
        assertEquals("value2", result.get("field2"));
    }

    @Test
    void testReadContentFromCache_WhenRedisParsingFails() throws Exception {
        List<String> fields = List.of("field1", "field2");
        String redisContent = "invalid-json";
        Map<String, Object> contentData = new HashMap<>();
        contentData.put("field1", "value1");
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        when(redisCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(redisContent);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenThrow(new RuntimeException("Parse error"));
        doReturn(contentData).when(contentService).readContent(CONTENT_ID);
        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);
        assertEquals(contentData, result);
    }

    @Test
    void testReadContentFromCache_WhenNoCacheData() {
        List<String> fields = List.of("field1", "field2");
        Map<String, Object> contentData = new HashMap<>();
        contentData.put("field1", "value1");
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        when(redisCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        doReturn(contentData).when(contentService).readContent(CONTENT_ID, fields);
        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);
        assertEquals(contentData, result);
    }

    @Test
    void testReadContentFromCache_WhenDataCacheHasLessFields() {
        List<String> fields = List.of("field1", "field2", "field3");
        Map<String, Object> cachedData = new HashMap<>();
        cachedData.put("field1", "value1");
        Map<String, Object> contentData = new HashMap<>();
        contentData.put("field1", "value1");
        contentData.put("field2", "value2");
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(cachedData);
        when(redisCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        doReturn(contentData).when(contentService).readContent(CONTENT_ID, fields);
        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);
        assertEquals(contentData, result);
    }

    @Test
    void readContent_WithValidContentIdAndFields_ReturnsContent() {
        List<String> fields = Arrays.asList("field1", "field2");
        when(serverConfig.getContentHost()).thenReturn("http://content-host");
        when(serverConfig.getContentReadEndPoint()).thenReturn("/read");
        when(serverConfig.getContentReadEndPointFields()).thenReturn("/fields");
        Map<String, Object> content = new HashMap<>();
        content.put("key", "value");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.CONTENT, content);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        Map<String, Object> actualContent = contentService.readContent(CONTENT_ID, fields);
        assertNotNull(actualContent);
        assertEquals(content, actualContent);
    }

    @Test
    void readContent_WithValidContentIdNoFields_ReturnsContent() {
        when(serverConfig.getContentHost()).thenReturn("http://content-host");
        when(serverConfig.getContentReadEndPoint()).thenReturn("/read");
        when(serverConfig.getContentReadEndPointFields()).thenReturn("/fields");
        Map<String, Object> content = new HashMap<>();
        content.put("key", "value");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.CONTENT, content);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        Map<String, Object> actualContent = contentService.readContent(CONTENT_ID, null);
        assertNotNull(actualContent);
        assertEquals(content, actualContent);
    }

    @Test
    void readContent_WithNonOkResponseCode_ReturnsNull() {
        List<String> fields = Arrays.asList("field1");
        when(serverConfig.getContentHost()).thenReturn("http://content-host");
        when(serverConfig.getContentReadEndPoint()).thenReturn("/read");
        when(serverConfig.getContentReadEndPointFields()).thenReturn("/fields");
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, "ERROR");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);
        Map<String, Object> actualContent = contentService.readContent(CONTENT_ID, fields);
        assertNotNull(actualContent);
        assertTrue(actualContent.isEmpty());
    }

    @Test
    void readContent_WithRestTemplateException_ReturnsNull() {
        List<String> fields = Arrays.asList("field1");
        when(serverConfig.getContentHost()).thenReturn("http://content-host");
        when(serverConfig.getContentReadEndPoint()).thenReturn("/read");
        when(serverConfig.getContentReadEndPointFields()).thenReturn("/fields");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenThrow(new RestClientException("Connection failed"));
        Map<String, Object> actualContent = contentService.readContent(CONTENT_ID, fields);

        assertNotNull(actualContent);
        assertTrue(actualContent.isEmpty());
    }

    @Test
    void readContent_WithCachedContent_ReturnsFromCache() {
        List<String> fields = List.of("field1");
        when(serverConfig.getContentHost()).thenReturn("http://content-host");
        when(serverConfig.getContentReadEndPoint()).thenReturn("/read");
        when(serverConfig.getContentReadEndPointFields()).thenReturn("/fields");
        Map<String, Object> content = new HashMap<>();
        content.put("key", "cached-value");
        Map<String, Object> result = new HashMap<>();
        result.put(Constants.CONTENT, content);
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);
        String expectedUrl = "http://content-host/read/test-content-id/fields,field1";
        when(restTemplate.getForObject(expectedUrl, Map.class)).thenReturn(response);
        Map<String, Object> actualContent = contentService.readContent(CONTENT_ID, fields);
        assertNotNull(actualContent);
        assertEquals("cached-value", actualContent.get("key"));
        verify(restTemplate).getForObject(expectedUrl, Map.class);
    }

    @Test
    void readContent_WithNullRestResponse_ReturnsNull() {
        List<String> fields = Arrays.asList("field1");
        when(serverConfig.getContentHost()).thenReturn("http://content-host");
        when(serverConfig.getContentReadEndPoint()).thenReturn("/read");
        when(serverConfig.getContentReadEndPointFields()).thenReturn("/fields");
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);
        Map<String, Object> actualContent = contentService.readContent(CONTENT_ID, fields);
        assertNotNull(actualContent);
        assertTrue(actualContent.isEmpty());
    }

    @Test
    void fetchResult_SuccessfulResponse_ReturnsResponse() {
        String uri = "http://test-uri.com";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        when(restTemplate.getForObject(uri, Map.class)).thenReturn(expectedResponse);
        Object actualResponse = contentService.fetchResult(uri);
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).getForObject(uri, Map.class);
    }

    @Test
    void fetchResult_HttpClientErrorException_ReturnsErrorResponse() {
        String uri = "http://test-uri.com";
        String errorResponse = "{\"error\":\"Not Found\"}";
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found", errorResponse.getBytes(), StandardCharsets.UTF_8);
        when(restTemplate.getForObject(uri, Map.class)).thenThrow(exception);
        Object actualResponse = contentService.fetchResult(uri);
        assertNotNull(actualResponse);
        assertTrue(actualResponse instanceof Map);
        Map<String, Object> responseMap = (Map<String, Object>) actualResponse;
        assertEquals("Not Found", responseMap.get("error")); // ✅ Fix here
    }

    @Test
    void fetchResult_HttpClientErrorExceptionWithInvalidJson_ReturnsNull() {
        String uri = "http://test-uri.com";
        String invalidJsonResponse = "invalid json";
        HttpClientErrorException exception = new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found", invalidJsonResponse.getBytes(), StandardCharsets.UTF_8);
        when(restTemplate.getForObject(uri, Map.class)).thenThrow(exception);
        Object actualResponse = contentService.fetchResult(uri);
        assertNull(actualResponse);
    }

    @Test
    void fetchResult_GenericException_ReturnsNull() {
        String uri = "http://test-uri.com";
        when(restTemplate.getForObject(uri, Map.class)).thenThrow(new RuntimeException("Generic error"));
        Object actualResponse = contentService.fetchResult(uri);
        assertNull(actualResponse);
    }

    @Test
    void fetchResult_GenericExceptionWithResponse_ReturnsNull() {
        String uri = "http://test-uri.com";
        Map<String, Object> response = new HashMap<>();
        response.put("key", "value");
        when(restTemplate.getForObject(uri, Map.class)).thenAnswer(invocation -> {
            throw new RuntimeException("Generic error");
        });
        Object actualResponse = contentService.fetchResult(uri);
        assertNull(actualResponse);
    }

    @Test
    void fetchResult_SuccessfulResponse_ReturnsResponseAndLogs() {
        String uri = "http://test-uri.com";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");
        when(restTemplate.getForObject(uri, Map.class)).thenReturn(expectedResponse);
        Object actualResponse = contentService.fetchResult(uri);
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void fetchResult_NullResponse_ReturnsNull() {
        String uri = "http://test-uri.com";
        when(restTemplate.getForObject(uri, Map.class)).thenReturn(null);
        Object actualResponse = contentService.fetchResult(uri);
        assertNull(actualResponse);
    }

    @Test
    void readContent_WithContentId_CallsOverloadedMethod() {
        // Arrange
        String contentId = "test-content-id";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");

        // Setup response structure
        Map<String, Object> content = new HashMap<>();
        content.put("key", "value");

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.CONTENT, content);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);

        // Mock REST template response
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        // Act
        Map<String, Object> actualResponse = contentService.readContent(contentId);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
        verify(restTemplate).getForObject(anyString(), eq(Map.class));
    }

    @Test
    void readContent_WithNullContentId_ReturnsNull() {
        // Act
        Map<String, Object> actualResponse = contentService.readContent(null);

        // Assert
        assertNotNull(actualResponse);
        assertTrue(actualResponse.isEmpty());
    }

    @Test
    void readContent_WithEmptyContentId_ReturnsNull() {
        // Act
        Map<String, Object> actualResponse = contentService.readContent("");

        // Assert
        assertNotNull(actualResponse);
        assertTrue(actualResponse.isEmpty());
    }

    @Test
    void readContent_WhenOverloadedMethodReturnsNull_ReturnsNull() {
        // Arrange
        String contentId = "test-content-id";

        // Mock REST template to return response that will result in null
        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, "ERROR");  // Non-OK response code

        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(response);

        // Act
        Map<String, Object> actualResponse = contentService.readContent(contentId);

        // Assert
        assertNotNull(actualResponse);
        assertTrue(actualResponse.isEmpty());
    }

    @Test
    void readContent_VerifiesEmptyFieldsList() {
        // Arrange
        String contentId = "test-content-id";
        Map<String, Object> expectedResponse = new HashMap<>();
        expectedResponse.put("key", "value");

        // Setup response structure
        Map<String, Object> content = new HashMap<>();
        content.put("key", "value");

        Map<String, Object> result = new HashMap<>();
        result.put(Constants.CONTENT, content);

        Map<String, Object> response = new HashMap<>();
        response.put(Constants.RESPONSE_CODE, Constants.OK);
        response.put(Constants.RESULT, result);

        // Mock REST template response
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        when(restTemplate.getForObject(urlCaptor.capture(), eq(Map.class))).thenReturn(response);

        // Act
        Map<String, Object> actualResponse = contentService.readContent(contentId);

        // Assert
        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);

        // Verify the URL doesn't contain any fields
        String capturedUrl = urlCaptor.getValue();
        assertFalse(capturedUrl.contains("field"));
    }

    // ---------------------------------------------------------------------
    // readContentFromCache - branches not reached by the tests above:
    // the redis payload parses to a *non-null but empty* map (the
    // MapUtils.isNotEmpty(contentData) guard's false branch, which skips both the
    // field-copy loop and dataCacheMgr.putContentInCache), and a requested field that
    // is simply absent from the parsed content (the contentData.containsKey(field)
    // guard's false branch inside that loop).
    // ---------------------------------------------------------------------

    @Test
    void testReadContentFromCache_WhenRedisDataParsesToEmptyMap_skipsCacheWrite() throws Exception {
        List<String> fields = List.of("field1", "field2");
        String redisContent = "{}";
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        when(redisCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(redisContent);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenReturn(new HashMap<>());

        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(dataCacheMgr, never()).putContentInCache(anyString(), any(Map.class));
    }

    @Test
    void testReadContentFromCache_WhenRequestedFieldMissingFromParsedContent_skipsField() throws Exception {
        List<String> fields = List.of("field1", "field2");
        String redisContent = "{\"field1\":\"value1\"}";
        Map<String, Object> contentData = new HashMap<>();
        contentData.put("field1", "value1"); // field2 intentionally absent
        when(dataCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(null);
        when(redisCacheMgr.getContentFromCache(CONTENT_ID)).thenReturn(redisContent);
        when(mapper.readValue(anyString(), any(TypeReference.class))).thenReturn(contentData);

        Map<String, Object> result = contentService.readContentFromCache(CONTENT_ID, fields);

        assertEquals(1, result.size());
        assertEquals("value1", result.get("field1"));
        assertFalse(result.containsKey("field2"));
        verify(dataCacheMgr).putContentInCache(eq(CONTENT_ID), any(Map.class));
    }

    // ---------------------------------------------------------------------
    // fetchResult - the log.isDebugEnabled() branch. No existing test runs with DEBUG
    // logging enabled for this class, so that whole guarded block (building the debug
    // string and calling log.debug/log.info) is never executed. Flipping the logger's
    // level directly is the only way to exercise it without changing test config globally.
    // ---------------------------------------------------------------------

    @Test
    void fetchResult_debugLoggingEnabled_stillReturnsResponse() {
        org.slf4j.Logger sl4jLogger = LoggerFactory.getLogger(ContentServiceImpl.class);
        ch.qos.logback.classic.Logger logbackLogger = (ch.qos.logback.classic.Logger) sl4jLogger;
        ch.qos.logback.classic.Level originalLevel = logbackLogger.getLevel();
        logbackLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            String uri = "http://test-uri.com/debug-check";
            Map<String, Object> expectedResponse = new HashMap<>();
            expectedResponse.put("key", "value");
            when(restTemplate.getForObject(uri, Map.class)).thenReturn(expectedResponse);

            Object actualResponse = contentService.fetchResult(uri);

            assertNotNull(actualResponse);
            assertEquals(expectedResponse, actualResponse);
        } finally {
            logbackLogger.setLevel(originalLevel);
        }
    }

}
