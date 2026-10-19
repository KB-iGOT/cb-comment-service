package com.tarento.commenthub.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.utils.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
    private CassandraUtil cassandraUtil;

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

        when(cassandraUtil.getPreparedStatement(anyString(), anyString(), any())).thenReturn("INSERT INTO testKeyspace.testTable (id, name) VALUES (?, ?)");

        when(mockSession.prepare(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.bind(any(Object[].class))).thenReturn(mockBoundStatement);
        when(mockSession.execute(any(BoundStatement.class))).thenReturn(mockResultSet);

        // Act
        ApiResponse response = (ApiResponse) cassandraOperation.insertRecord(keyspaceName, tableName, request);

        // Assert
        assertEquals("success", response.get(Constants.RESPONSE));
        verify(mockSession).prepare(anyString());
        verify(mockSession).execute(mockBoundStatement);
    }

    @Test
    void insertRecord_Exception() {
        // Arrange
        Map<String, Object> request = new HashMap<>();
        request.put("id", "123");

        when(cassandraUtil.getPreparedStatement(anyString(), anyString(), any())).thenReturn("INSERT INTO testKeyspace.testTable (id) VALUES (?)");

        when(mockSession.prepare(anyString())).thenReturn(mockPreparedStatement);
        when(mockPreparedStatement.bind(any())).thenReturn(mockBoundStatement);
        when(mockSession.execute(any(BoundStatement.class))).thenThrow(new RuntimeException("Test exception"));

        // Act
        ApiResponse response = (ApiResponse) cassandraOperation.insertRecord(keyspaceName, tableName, request);

        // Assert
        assertEquals("Failed", response.get(Constants.RESPONSE));
        assertNotNull(response.get(Constants.ERROR_MESSAGE));
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_WithFields() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");
        List<String> fields = Arrays.asList("id", "name");

        List<Map<String, Object>> expectedResponse = new ArrayList<>();
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("id", "123");
        recordMap.put("name", "Test");
        expectedResponse.add(recordMap);

        when(cassandraUtil.createResponse(any(ResultSet.class))).thenReturn(expectedResponse);

        when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

        // Act
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(keyspaceName, tableName, propertyMap, fields, 10);

        // Assert
        assertEquals(1, response.size());
        assertEquals("123", response.get(0).get("id"));
        assertEquals("Test", response.get(0).get("name"));
    }

    @Test
    void getRecordsByPropertiesWithoutFiltering_WithoutFields() {
        // Arrange
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("id", "123");

        List<Map<String, Object>> expectedResponse = new ArrayList<>();
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("id", "123");
        recordMap.put("name", "Test");
        expectedResponse.add(recordMap);

        when(cassandraUtil.createResponse(any(ResultSet.class))).thenReturn(expectedResponse);

        when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

        // Act
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesWithoutFiltering(keyspaceName, tableName, propertyMap, null, null);

        // Assert
        assertEquals(1, response.size());
        assertEquals("123", response.get(0).get("id"));
        assertEquals("Test", response.get(0).get("name"));
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
        String localKeyspaceName = "test_keyspace";
        String localTableName = "test_table";
        Map<String, Object> propertyMap = Map.of("id", 1);
        List<String> fields = List.of("id", "name");
        String key = "id";

        when(connectionManager.getSession(localKeyspaceName)).thenReturn(mockSession);
        when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

        List<Map<String, Object>> mockedResponse = List.of(Map.of("id", 1, "name", "Test"));
        when(cassandraUtil.createResponse(mockResultSet)).thenReturn(mockedResponse);

        // Call method
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(localKeyspaceName, localTableName, propertyMap, fields, key);

        // Assertions
        assertEquals(mockedResponse, response);
    }

    @Test
    void testGetRecordsByPropertiesByKey_withListValueProperty_buildsInClause() {
        String localKeyspaceName = "test_keyspace";
        String localTableName = "test_table";
        Map<String, Object> propertyMap = Map.of("id", List.of("1", "2", "3"));
        List<String> fields = List.of("id", "name");

        when(connectionManager.getSession(localKeyspaceName)).thenReturn(mockSession);
        when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

        List<Map<String, Object>> mockedResponse = List.of(Map.of("id", 1, "name", "Test"));
        when(cassandraUtil.createResponse(mockResultSet)).thenReturn(mockedResponse);

        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(
                localKeyspaceName, localTableName, propertyMap, fields, "id");

        assertEquals(mockedResponse, response);
    }

    @Test
    void testGetRecordsByPropertiesByKey_emptyPropertyMap_selectsAll() {
        String localKeyspaceName = "test_keyspace";
        String localTableName = "test_table";

        when(connectionManager.getSession(localKeyspaceName)).thenReturn(mockSession);
        when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);

        List<Map<String, Object>> mockedResponse = List.of(Map.of("id", 1));
        when(cassandraUtil.createResponse(mockResultSet)).thenReturn(mockedResponse);

        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(
                localKeyspaceName, localTableName, Collections.emptyMap(), List.of("id"), "id");

        assertEquals(mockedResponse, response);
    }

    @Test
    void testGetRecordsByPropertiesWithoutFiltering_fourArgOverload_delegatesWithNullLimit() {
        Map<String, Object> propertyMap = Map.of("id", "123");
        List<String> fields = List.of("id");

        when(mockSession.execute(any(SimpleStatement.class))).thenReturn(mockResultSet);
        List<Map<String, Object>> mockedResponse = List.of(Map.of("id", "123"));
        when(cassandraUtil.createResponse(mockResultSet)).thenReturn(mockedResponse);

        List<Map<String, Object>> response =
                cassandraOperation.getRecordsByPropertiesWithoutFiltering(keyspaceName, tableName, propertyMap, fields);

        assertEquals(mockedResponse, response);
    }

    @Test
    void testGetRecordsByPropertiesByKey_exception() {
        // Prepare input
        String localKeyspaceName = "test_keyspace";
        String localTableName = "test_table";
        Map<String, Object> propertyMap = Map.of("id", 1);
        List<String> fields = List.of("id", "name");
        String key = "id";

        // Throw exception
        when(connectionManager.getSession(anyString())).thenThrow(new RuntimeException("Connection failed"));

        // Call method
        List<Map<String, Object>> response = cassandraOperation.getRecordsByPropertiesByKey(localKeyspaceName, localTableName, propertyMap, fields, key);

        // Assert
        assertNotNull(response); // should return empty list
        assertTrue(response.isEmpty());
    }
}