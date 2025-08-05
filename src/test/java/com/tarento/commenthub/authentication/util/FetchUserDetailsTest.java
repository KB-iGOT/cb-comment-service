package com.tarento.commenthub.authentication.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FetchUserDetailsTest {

    @InjectMocks
    private FetchUserDetails fetchUserDetails;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fetchDataForKeys_shouldReturnDeserializedObjects() throws JsonProcessingException {
        List<String> keys = Arrays.asList("user1", "user2");
        String json1 = new ObjectMapper().writeValueAsString(Collections.singletonMap("id", "user1"));
        String json2 = new ObjectMapper().writeValueAsString(Collections.singletonMap("id", "user2"));
        List<Object> redisValues = Arrays.asList(json1, json2);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(keys)).thenReturn(redisValues);

        List<Object> result = fetchUserDetails.fetchDataForKeys(keys);

        assertEquals(2, result.size());
        assertTrue(result.get(0) instanceof Map);
        assertEquals("user1", ((Map<?, ?>) result.get(0)).get("id"));
    }

    @Test
    void fetchDataForKeys_shouldHandleJsonProcessingErrorGracefully() {
        List<String> keys = List.of("user1");
        List<Object> redisValues = List.of("invalid_json");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.multiGet(keys)).thenReturn(redisValues);

        List<Object> result = fetchUserDetails.fetchDataForKeys(keys);

        assertEquals(1, result.size());
        assertNull(result.get(0));  // Error handled, null returned
    }

    @Test
    void fetchUserFromprimary_shouldReturnUserDetailsWithProfileInfo(){
        String profileJson = "{\"profileImage\":\"img.jpg\",\"employmentDetails\":{\"department\":\"HR\"},\"designation\":\"Manager\"}";

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
    void fetchUserFromprimary_shouldHandleInvalidJsonGracefully() {
        Map<String, Object> dbRecord = new HashMap<>();
        dbRecord.put(Constants.ID, "user456");
        dbRecord.put(Constants.FIRST_NAME, "Bob");
        dbRecord.put(Constants.PROFILE_DETAILS, "invalid_json");

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                anyString(), anyString(), anyMap(), anyList(), any()))
                .thenReturn(Collections.singletonList(dbRecord));

        assertThrows(RuntimeException.class, () ->
                fetchUserDetails.fetchUserFromprimary(List.of("user456")));
    }
}
