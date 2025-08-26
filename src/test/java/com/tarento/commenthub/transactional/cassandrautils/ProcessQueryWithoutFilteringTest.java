package com.tarento.commenthub.transactional.cassandrautils;

import static org.junit.jupiter.api.Assertions.*;

import com.datastax.oss.driver.api.querybuilder.select.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class ProcessQueryWithoutFilteringTest {

    @InjectMocks
    private CassandraOperationImpl cassandraOperation;

    private Method processQueryWithoutFilteringMethod;

    @BeforeEach
    void setUp() throws Exception {
        processQueryWithoutFilteringMethod = CassandraOperationImpl.class
            .getDeclaredMethod("processQueryWithoutFiltering", String.class, String.class, Map.class, List.class);
        processQueryWithoutFilteringMethod.setAccessible(true);
    }

    @Test
    void testWithFieldsAndEmptyPropertyMap() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        List<String> fields = Arrays.asList("field1", "field2");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertTrue(query.contains("SELECT field1,field2"));
        assertTrue(query.contains("FROM test_keyspace.test_table"));
    }

    @Test
    void testWithoutFieldsAndEmptyPropertyMap() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        List<String> fields = null;

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertTrue(query.contains("SELECT *"));
        assertTrue(query.contains("FROM test_keyspace.test_table"));
    }

    @Test
    void testWithEmptyFieldsAndEmptyPropertyMap() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        List<String> fields = new ArrayList<>();

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertTrue(query.contains("SELECT *"));
        assertTrue(query.contains("FROM test_keyspace.test_table"));
    }

    @Test
    void testWithNullPropertyMap() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = null;
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertTrue(query.contains("SELECT field1"));
        assertTrue(query.contains("FROM test_keyspace.test_table"));
    }

    @Test
    void testWithSingleValueProperty() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("userId", "user123");
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
    }

    @Test
    void testWithListValueProperty() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("userIds", Arrays.asList("user1", "user2", "user3"));
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
    }

    @Test
    void testWithEmptyListProperty() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("userIds", new ArrayList<>());
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertFalse(query.contains("WHERE"));
    }

    @Test
    void testWithNullListProperty() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("userIds", null);
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
    }

    @Test
    void testWithMultipleProperties() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("userId", "user123");
        propertyMap.put("status", "active");
        propertyMap.put("tags", Arrays.asList("tag1", "tag2"));
        List<String> fields = Arrays.asList("field1", "field2");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertTrue(query.contains("WHERE"));
    }

    @Test
    void testWithIntegerValue() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        propertyMap.put("count", 42);
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
        String query = result.toString();
        assertTrue(query.contains("WHERE count=42"));
    }

    @Test
    void testWithMixedListTypes() throws Exception {
        String keyspaceName = "test_keyspace";
        String tableName = "test_table";
        Map<String, Object> propertyMap = new HashMap<>();
        List<Object> mixedList = Arrays.asList("string1", 123, "string2");
        propertyMap.put("mixedField", mixedList);
        List<String> fields = Arrays.asList("field1");

        Select result = (Select) processQueryWithoutFilteringMethod.invoke(
            cassandraOperation, keyspaceName, tableName, propertyMap, fields);

        assertNotNull(result);
    }
}