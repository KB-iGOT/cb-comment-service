package com.tarento.commenthub.authentication.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FetchUserDetailsTest {

    @InjectMocks
    private FetchUserDetails fetchUserDetails;

    @Mock
    private JedisPool jedisPool;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private Jedis jedis;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(jedisPool.getResource()).thenReturn(jedis);
    }

    @Test
    void fetchDataForKeys_shouldReturnDeserializedObjects() throws JsonProcessingException {
        List<String> keys = Arrays.asList("user1", "user2");
        String json1 = new ObjectMapper().writeValueAsString(Collections.singletonMap("id", "user1"));
        String json2 = new ObjectMapper().writeValueAsString(Collections.singletonMap("id", "user2"));
        List<String> redisValues = Arrays.asList(json1, json2);

        when(jedis.mget("user1", "user2")).thenReturn(redisValues);

        List<Object> result = fetchUserDetails.fetchDataForKeys(keys);

        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Map);
        assertEquals("user1", ((Map<?, ?>) result.get(0)).get("id"));
        verify(jedis).close();
    }

    @Test
    void fetchDataForKeys_shouldHandleJsonProcessingErrorGracefully() {
        List<String> keys = List.of("user1");
        List<String> redisValues = List.of("invalid_json");

        when(jedis.mget("user1")).thenReturn(redisValues);

        List<Object> result = fetchUserDetails.fetchDataForKeys(keys);

        assertEquals(0, result.size()); // Error handled, no objects added
        verify(jedis).close();
    }

    @Test
    void fetchDataForKeys_shouldHandleNullValues() {
        List<String> keys = List.of("user1");
        List<String> redisValues = Arrays.asList((String) null);

        when(jedis.mget("user1")).thenReturn(redisValues);

        List<Object> result = fetchUserDetails.fetchDataForKeys(keys);

        assertEquals(0, result.size());
        verify(jedis).close();
    }

    @Test
    void fetchUserFromprimary_shouldReturnUserDetailsWithProfileInfo() {
        String profileJson = "{\"profileImg\":\"img.jpg\",\"employmentDetails\":{\"department\":\"HR\"},\"designation\":\"Manager\"}";

        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user123");
        dbRecord.put(Constants.FIRST_NAME, "Alice");
        dbRecord.put(Constants.PROFILE_DETAILS, profileJson);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), eq(Constants.TABLE_USER), anyMap(), anyList(), isNull()))
                .thenReturn(Collections.singletonList(dbRecord));

        List<Object> users = fetchUserDetails.fetchUserFromprimary(List.of("user123"));

        assertEquals(1, users.size());
        Map<String, Object> userMap = (Map<String, Object>) users.get(0);

        assertEquals("user123", userMap.get(Constants.USER_ID_KEY));
        assertEquals("Alice", userMap.get(Constants.FIRST_NAME_KEY));
    }

    @Test
    void fetchUserFromprimary_shouldHandleEmptyProfileDetails() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user123");
        dbRecord.put(Constants.FIRST_NAME, "Alice");
        dbRecord.put(Constants.PROFILE_DETAILS, "");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(Collections.singletonList(dbRecord));

        List<Object> users = fetchUserDetails.fetchUserFromprimary(List.of("user123"));

        assertEquals(1, users.size());
        Map<String, Object> userMap = (Map<String, Object>) users.get(0);
        assertEquals("user123", userMap.get(Constants.USER_ID_KEY));
        assertEquals("Alice", userMap.get(Constants.FIRST_NAME_KEY));
        assertFalse(userMap.containsKey(Constants.PROFILE_IMG_KEY));
    }

    @Test
    void fetchUserFromprimary_shouldHandleNullProfileDetails() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user123");
        dbRecord.put(Constants.FIRST_NAME, "Alice");
        dbRecord.put(Constants.PROFILE_DETAILS, null);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(Collections.singletonList(dbRecord));

        List<Object> users = fetchUserDetails.fetchUserFromprimary(List.of("user123"));

        assertEquals(1, users.size());
        Map<String, Object> userMap = (Map<String, Object>) users.get(0);
        assertEquals("user123", userMap.get(Constants.USER_ID_KEY));
        assertEquals("Alice", userMap.get(Constants.FIRST_NAME_KEY));
    }

    @Test
    void fetchUserFromprimary_shouldHandleInvalidJsonGracefully() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user456");
        dbRecord.put(Constants.FIRST_NAME, "Bob");
        dbRecord.put(Constants.PROFILE_DETAILS, "invalid_json");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(Collections.singletonList(dbRecord));

        List<String> users = List.of("user456");

        assertThrows(RuntimeException.class,
                () -> fetchUserDetails.fetchUserFromprimary(users));

    }

    @Test
    void fetchUserFromprimary_shouldHandleEmptyProfileDetailsMap() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user123");
        dbRecord.put(Constants.FIRST_NAME, "Alice");
        dbRecord.put(Constants.PROFILE_DETAILS, "{}");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(Collections.singletonList(dbRecord));

        List<Object> users = fetchUserDetails.fetchUserFromprimary(List.of("user123"));

        assertEquals(1, users.size());
        Map<String, Object> userMap = (Map<String, Object>) users.get(0);
        assertEquals("user123", userMap.get(Constants.USER_ID_KEY));
        assertEquals("Alice", userMap.get(Constants.FIRST_NAME_KEY));
    }
}