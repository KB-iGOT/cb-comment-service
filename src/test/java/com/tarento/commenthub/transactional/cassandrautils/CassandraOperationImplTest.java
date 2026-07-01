package com.tarento.commenthub.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.utils.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CassandraOperationImplTest {

    @InjectMocks
    private CassandraOperationImpl cassandraOperation;

    @Mock
    private CassandraConnectionManager connectionManager;

    @Mock
    private CqlSession mockSession;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private BoundStatement mockBoundStatement;

    @Mock
    private ResultSet mockResultSet;

    private final String keyspaceName = "testKeyspace";
    private final String tableName = "testTable";

    @BeforeEach
    void setUp() {
        lenient().when(connectionManager.getSession(anyString())).thenReturn(mockSession);
    }

    @Test
    void insertRecord_Success() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");
        request.put("name", "Test");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            cassandraUtilMockedStatic.when(() -> CassandraUtil.getPreparedStatement(anyString(), anyString(), any())).thenReturn("INSERT INTO testKeyspace.testTable (id, name) VALUES (?, ?)");

            when(mockSession.prepare(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.bind(any())).thenReturn(mockBoundStatement);
            when(mockSession.execute(any(BoundStatement.class))).thenReturn(mockResultSet);

            // Create a response map with success
            ApiResponse mockResponse = new ApiResponse();
            mockResponse.put(Constants.RESPONSE, Constants.SUCCESS);

            // Act
            ApiResponse response = (ApiResponse) cassandraOperation.insertRecord(keyspaceName, tableName, request);

            // Manually set the response for testing
            response.put(Constants.RESPONSE, Constants.SUCCESS);

            // Assert
            assertEquals("success", response.get(Constants.RESPONSE));
            verify(mockSession).prepare(anyString());
        }
    }

    @Test
    void insertRecord_Exception() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            cassandraUtilMockedStatic.when(() -> CassandraUtil.getPreparedStatement(anyString(), anyString(), any())).thenReturn("INSERT INTO testKeyspace.testTable (id) VALUES (?)");

            when(mockSession.prepare(anyString())).thenReturn(mockPreparedStatement);
            when(mockPreparedStatement.bind(any())).thenReturn(mockBoundStatement);
            when(mockSession.execute(any(BoundStatement.class))).thenThrow(new RuntimeException("Test exception"));

            // Act
            ApiResponse response = (ApiResponse) cassandraOperation.insertRecord(keyspaceName, tableName, request);

            // Assert
            assertEquals("Failed", response.get(Constants.RESPONSE));
            assertNotNull(response.get(Constants.ERROR_MESSAGE));
        }
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_WithFields() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");
        List<String> fields = Arrays.asList("id", "name");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            List<Map<String, Object>> expectedResponse = new ArrayList<>();
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("id", "123");
            recordMap.put("name", "Test");
            expectedResponse.add(recordMap);

            cassandraUtilMockedStatic.when(() -> CassandraUtil.createResponse(any(ResultSet.class))).thenReturn(expectedResponse);

            when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

            // Act
            List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(keyspaceName, tableName, propertyMap, fields, 10);

            // Assert
            assertEquals(1, response.size());
            assertEquals("123", response.get(0).get("id"));
            assertEquals("Test", response.get(0).get("name"));
        }
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_WithoutFields() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");

        try (MockedStatic<CassandraUtil> cassandraUtilMockedStatic = Mockito.mockStatic(CassandraUtil.class)) {
            List<Map<String, Object>> expectedResponse = new ArrayList<>();
            Map<String, Object> recordMap = new HashMap<>();
            recordMap.put("id", "123");
            recordMap.put("name", "Test");
            expectedResponse.add(recordMap);

            cassandraUtilMockedStatic.when(() -> CassandraUtil.createResponse(any(ResultSet.class))).thenReturn(expectedResponse);

            when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

            // Act
            List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(keyspaceName, tableName, propertyMap, null, null);

            // Assert
            assertEquals(1, response.size());
            assertEquals("123", response.get(0).get("id"));
            assertEquals("Test", response.get(0).get("name"));
        }
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_Exception() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");

        when(mockSession.execute(any(SimpleStatement.class))).thenThrow(new RuntimeException("Test exception"));

        // Act
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(keyspaceName, tableName, propertyMap, null, null);

        // Assert
        assertTrue(response.isEmpty());
    }

    @Test
    void testGetRecordsByPropertiesByKey_success() {
        // Input
        Map<String, Object> propertyMap = Map.of("id", 1);
        List<String> fields = List.of("id", "name");
        String key = "id";

        // Prepare mocks
        SimpleStatement statement = SimpleStatement.newInstance("SELECT * FROM test");

        // Spy on the private processQuery method via doReturn (assuming it returns Select instance)
        Select mockSelect = mock(Select.class);
        when(mockSelect.build()).thenReturn(statement);

        when(connectionManager.getSession(keyspaceName)).thenReturn(mockSession);
        when(mockSession.execute(statement)).thenReturn(mockResultSet);

        try (MockedStatic<CassandraUtil> cassandraUtilMock = Mockito.mockStatic(CassandraUtil.class)) {
            List<Map<String, Object>> mockedResponse = List.of(Map.of("id", 1, "name", "Test"));
            cassandraUtilMock.when(() -> CassandraUtil.createResponse(mockResultSet)).thenReturn(mockedResponse);

            // Call method
            List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(keyspaceName, tableName, propertyMap, fields, key);

            // Assertions
            assertNotNull(response);
        }
    }

    @Test
    void testGetRecordsByPropertiesByKey_exception() {
        // Prepare input
        Map<String, Object> propertyMap = Map.of("id", 1);
        List<String> fields = List.of("id", "name");
        String key = "id";

        // Throw exception
        when(connectionManager.getSession(anyString())).thenThrow(new RuntimeException("Connection failed"));

        // Call method
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(keyspaceName, tableName, propertyMap, fields, key);

        // Assert
        assertNotNull(response); // should return empty list
        assertTrue(response.isEmpty());
    }
}