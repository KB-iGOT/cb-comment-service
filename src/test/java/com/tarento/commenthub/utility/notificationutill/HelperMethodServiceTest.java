package com.tarento.commenthub.utility.notificationutill;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.service.ContentService;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import com.tarento.commenthub.utility.RedisCacheMngr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelperMethodServiceTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private CassandraOperation cassandraOperation;
    @Mock
    private RedisCacheMngr cacheService;
    @Mock
    private ContentService contentService;
    @Mock
    private NotificationTriggerService notificationTriggerService;

    @InjectMocks
    private HelperMethodService helperMethodService;
    
    private ObjectMapper realObjectMapper;

    @BeforeEach
    void setUp() {
        realObjectMapper = new ObjectMapper();
    }

    @Test
    void testFetchDataForKeys() {
        String key = "testKey";
        String expectedValue = "testValue";
        when(cacheService.getContentFromCache(key)).thenReturn(expectedValue);

        String result = helperMethodService.fetchDataForKeys(key);

        assertEquals(expectedValue, result);
        verify(cacheService).getContentFromCache(key);
    }

    @Test
    void testFetchUserFromPrimary_EmptyUserIds() {
        List<String> emptyUserIds = Collections.emptyList();

        List<Object> result = helperMethodService.fetchUserFromPrimary(emptyUserIds);

        assertTrue(result.isEmpty());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any());
    }

    @Test
    void testFetchUserFromPrimary_NullUserIds() {
        List<Object> result = helperMethodService.fetchUserFromPrimary(null);

        assertTrue(result.isEmpty());
        verify(cassandraOperation, never()).getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any());
    }

    @Test
    void testFetchUserFromPrimary_EmptyUserInfoList() {
        List<String> userIds = Arrays.asList("user1", "user2");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchUserFromPrimary_WithValidData() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "{\"profileImageUrl\":\"image.jpg\",\"designation\":\"Developer\",\"employmentDetails\":{\"departmentName\":\"IT\"}}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put(Constants.PROFILE_IMG, "image.jpg");
        profileDetailsMap.put(Constants.DESIGNATION_KEY, "Developer");
        Map<String, Object> employmentDetails = new HashMap<>();
        employmentDetails.put(Constants.DEPARTMENT_KEY, "IT");
        profileDetailsMap.put(Constants.EMPLOYMENT_DETAILS, employmentDetails);

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(profileDetailsMap);

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        verify(objectMapper).readValue(anyString(), any(TypeReference.class));
    }

    @Test
    void testFetchUserFromPrimary_WithBlankProfileDetails() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        verify(objectMapper, never()).readValue(anyString(), any(TypeReference.class));
    }

    @Test
    void testFetchUserFromPrimary_JsonProcessingException() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "{\"invalid\":\"json\"}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void testFetchUserFromPrimary_WithEmptyProfileDetailsMap() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "{}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(new HashMap<>());

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void testFetchUserFirstName_FromRedis() throws JsonProcessingException {
        String userId = "user1";
        String redisData = "{\"first_name\":\"John\"}";
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.FIRST_NAME_KEY, "John");

        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn(redisData);
        lenient().when(objectMapper.readValue(eq(redisData), any(TypeReference.class))).thenReturn(resultMap);

        String result = helperMethodService.fetchUserFirstName(userId);

        assertEquals("John", result);
    }

    @Test
    void testFetchUserFirstName_FromRedis_BlankName() throws JsonProcessingException {
        String userId = "user1";
        String redisData = "{\"first_name\":\"\"}";
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.FIRST_NAME_KEY, "");

        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn(redisData);
        lenient().when(objectMapper.readValue(eq(redisData), any(TypeReference.class))).thenReturn(resultMap);

        // Mock Cassandra call
        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.FIRST_NAME_KEY, "Jane");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(Collections.singletonMap(Constants.ID, userId)));

        String result = helperMethodService.fetchUserFirstName(userId);

        assertEquals("User", result);
    }

    @Test
    void testFetchUserFirstName_FromRedis_NonStringName() throws JsonProcessingException {
        String userId = "user1";
        String redisData = "{\"first_name\":123}";
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put(Constants.FIRST_NAME_KEY, 123);

        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn(redisData);
        lenient().when(objectMapper.readValue(eq(redisData), any(TypeReference.class))).thenReturn(resultMap);

        // Mock Cassandra call - need to return empty list to get "User" as default
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = helperMethodService.fetchUserFirstName(userId);

        assertEquals("User", result);
    }

    @Test
    void testFetchUserFirstName_JsonProcessingException() throws JsonProcessingException {
        String userId = "user1";
        String redisData = "invalid json";

        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn(redisData);
        lenient().when(objectMapper.readValue(eq(redisData), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        assertThrows(RuntimeException.class, () -> helperMethodService.fetchUserFirstName(userId));
    }

    @Test
    void testFetchUserFirstName_FromCassandra() {
        String userId = "user1";
        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn("");

        Map<String, Object> userMap = new HashMap<>();
        userMap.put(Constants.FIRST_NAME_KEY, "Jane");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(Collections.singletonMap(Constants.ID, userId)));

        String result = helperMethodService.fetchUserFirstName(userId);

        assertEquals("User", result);
    }

    @Test
    void testFetchUserFirstName_FromCassandra_WithValidName() {
        String userId = "user1";
        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn("");

        // Mock the Cassandra response to match what fetchUserFromPrimary returns
        Map<String, Object> cassandraUserInfo = new HashMap<>();
        cassandraUserInfo.put(Constants.ID, userId);
        cassandraUserInfo.put(Constants.FIRST_NAME, "Jane");
        cassandraUserInfo.put(Constants.PROFILE_DETAILS, "");
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Arrays.asList(cassandraUserInfo));

        String result = helperMethodService.fetchUserFirstName(userId);

        assertEquals("Jane", result);
    }

    @Test
    void testFetchUserFirstName_EmptyCassandraResults() {
        String userId = "user1";
        when(cacheService.getContentFromCache(Constants.USER_PREFIX + userId)).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = helperMethodService.fetchUserFirstName(userId);

        assertEquals("User", result);
    }

    @Test
    void testDecodeJwtAndFetchCourseId() {
        String commentTreeId = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJlbnRpdHlJZCI6ImNvdXJzZTEyMyJ9.test";
        
        try (MockedStatic<JWT> jwtMock = mockStatic(JWT.class)) {
            DecodedJWT decodedJWT = mock(DecodedJWT.class);
            Claim claim = mock(Claim.class);
            
            jwtMock.when(() -> JWT.decode(commentTreeId)).thenReturn(decodedJWT);
            when(decodedJWT.getClaim(Constants.ENTITY_ID)).thenReturn(claim);
            when(claim.asString()).thenReturn("course123");

            String result = helperMethodService.decodeJwtAndFetchCourseId(commentTreeId);

            assertEquals("course123", result);
        }
    }

    @Test
    void testProcessMentionedUsers_WithExistingAndNewUsers() {
        ObjectNode data = realObjectMapper.createObjectNode();
        ArrayNode existingUsers = realObjectMapper.createArrayNode();
        ObjectNode existingUser = realObjectMapper.createObjectNode();
        existingUser.put(Constants.USER_ID, "user1");
        existingUsers.add(existingUser);
        data.set(Constants.MENTIONED_USERS, existingUsers);

        ObjectNode updateDataNode = realObjectMapper.createObjectNode();
        ArrayNode incomingUsers = realObjectMapper.createArrayNode();
        ObjectNode newUser = realObjectMapper.createObjectNode();
        newUser.put(Constants.USER_ID, "user2");
        ObjectNode duplicateUser = realObjectMapper.createObjectNode();
        duplicateUser.put(Constants.USER_ID, "user2");
        incomingUsers.add(newUser);
        incomingUsers.add(duplicateUser);
        updateDataNode.set(Constants.MENTIONED_USERS, incomingUsers);
        
        when(objectMapper.createArrayNode()).thenReturn(realObjectMapper.createArrayNode());

        List<String> result = helperMethodService.processMentionedUsers(data, updateDataNode);

        assertEquals(1, result.size());
        assertEquals("user2", result.get(0));
    }

    @Test
    void testProcessMentionedUsers_WithBlankUserId() {
        ObjectNode data = realObjectMapper.createObjectNode();
        data.set(Constants.MENTIONED_USERS, realObjectMapper.createArrayNode());

        ObjectNode updateDataNode = realObjectMapper.createObjectNode();
        ArrayNode incomingUsers = realObjectMapper.createArrayNode();
        ObjectNode userWithBlankId = realObjectMapper.createObjectNode();
        userWithBlankId.put(Constants.USER_ID, "");
        incomingUsers.add(userWithBlankId);
        updateDataNode.set(Constants.MENTIONED_USERS, incomingUsers);
        
        when(objectMapper.createArrayNode()).thenReturn(realObjectMapper.createArrayNode());

        List<String> result = helperMethodService.processMentionedUsers(data, updateDataNode);

        assertTrue(result.isEmpty());
    }

    @Test
    void testProcessMentionedUsers_WithNullIncomingUsers() {
        ObjectNode data = realObjectMapper.createObjectNode();
        data.set(Constants.MENTIONED_USERS, realObjectMapper.createArrayNode());

        ObjectNode updateDataNode = realObjectMapper.createObjectNode();
        
        when(objectMapper.createArrayNode()).thenReturn(realObjectMapper.createArrayNode());

        List<String> result = helperMethodService.processMentionedUsers(data, updateDataNode);

        assertTrue(result.isEmpty());
    }

    @Test
    void testSendNotificationToUser_WithCommentTreeId() {
        ObjectNode commentPayload = realObjectMapper.createObjectNode();
        ObjectNode commentData = realObjectMapper.createObjectNode();
        ObjectNode commentSource = realObjectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "user1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        commentPayload.set(Constants.COMMENT_DATA, commentData);
        commentPayload.put(Constants.COMMENT_TREE_ID, "jwt_token");

        String commentId = "comment123";
        List<String> userIdList = Arrays.asList("user1", "user2", "user3");

        // Mock fetchUserFirstName
        when(cacheService.getContentFromCache(anyString())).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Mock decodeJwtAndFetchCourseId
        try (MockedStatic<JWT> jwtMock = mockStatic(JWT.class)) {
            DecodedJWT decodedJWT = mock(DecodedJWT.class);
            Claim claim = mock(Claim.class);
            
            jwtMock.when(() -> JWT.decode("jwt_token")).thenReturn(decodedJWT);
            when(decodedJWT.getClaim(Constants.ENTITY_ID)).thenReturn(claim);
            when(claim.asString()).thenReturn("course123");

            Map<String, Object> courseResponse = new HashMap<>();
            courseResponse.put("name", "Test Course");
            when(contentService.readContentFromCache(eq("course123"), eq(Arrays.asList(Constants.NAME)))).thenReturn(courseResponse);

            helperMethodService.sendNotificationToUser(commentPayload, commentId, userIdList);

            verify(notificationTriggerService).triggerNotification(
                    eq(Constants.LEARN_DISCUSSION_POST_COMMENT),
                    eq(Constants.ENGAGEMENT),
                    eq(Arrays.asList("user2", "user3")),
                    eq("User"),
                    eq("Test Course"),
                    any(Map.class)
            );
        }
    }

    @Test
    void testSendNotificationToUser_WithCommentTreeData() {
        ObjectNode commentPayload = realObjectMapper.createObjectNode();
        ObjectNode commentData = realObjectMapper.createObjectNode();
        ObjectNode commentSource = realObjectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "user1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        commentPayload.set(Constants.COMMENT_DATA, commentData);

        ObjectNode commentTreeData = realObjectMapper.createObjectNode();
        commentTreeData.put(Constants.ENTITY_ID, "course456");
        commentPayload.set(Constants.COMMENT_TREE_DATA, commentTreeData);

        String commentId = "comment123";
        List<String> userIdList = Arrays.asList("user2");

        // Mock fetchUserFirstName
        when(cacheService.getContentFromCache(anyString())).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> courseResponse = new HashMap<>();
        courseResponse.put("name", "Another Course");
        when(contentService.readContentFromCache(eq("course456"), eq(Arrays.asList(Constants.NAME)))).thenReturn(courseResponse);

        helperMethodService.sendNotificationToUser(commentPayload, commentId, userIdList);

        verify(notificationTriggerService).triggerNotification(
                eq(Constants.LEARN_DISCUSSION_POST_COMMENT),
                eq(Constants.ENGAGEMENT),
                eq(Arrays.asList("user2")),
                eq("User"),
                eq("Another Course"),
                any(Map.class)
        );
    }

    @Test
    void testSendNotificationToUser_WithHierarchyPath() {
        ObjectNode commentPayload = realObjectMapper.createObjectNode();
        ObjectNode commentData = realObjectMapper.createObjectNode();
        ObjectNode commentSource = realObjectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "user1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        commentPayload.set(Constants.COMMENT_DATA, commentData);

        ObjectNode commentTreeData = realObjectMapper.createObjectNode();
        commentTreeData.put(Constants.ENTITY_ID, "course456");
        commentPayload.set(Constants.COMMENT_TREE_DATA, commentTreeData);

        ArrayNode hierarchyPath = realObjectMapper.createArrayNode();
        hierarchyPath.add("parent_comment_id");
        commentPayload.set(Constants.HIERARCHY_PATH, hierarchyPath);

        String commentId = "comment123";
        List<String> userIdList = Arrays.asList("user2");

        // Mock fetchUserFirstName
        when(cacheService.getContentFromCache(anyString())).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        Map<String, Object> courseResponse = new HashMap<>();
        courseResponse.put("name", "Reply Course");
        when(contentService.readContentFromCache(eq("course456"), eq(Arrays.asList(Constants.NAME)))).thenReturn(courseResponse);

        helperMethodService.sendNotificationToUser(commentPayload, commentId, userIdList);

        verify(notificationTriggerService).triggerNotification(
                eq(Constants.LEARN_DISCUSSION_POST_REPLY),
                eq(Constants.ENGAGEMENT),
                eq(Arrays.asList("user2")),
                eq("User"),
                eq("Reply Course"),
                any(Map.class)
        );
    }

    @Test
    void testSendNotificationToUser_EmptyFilteredUserList() {
        ObjectNode commentPayload = realObjectMapper.createObjectNode();
        ObjectNode commentData = realObjectMapper.createObjectNode();
        ObjectNode commentSource = realObjectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "user1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        commentPayload.set(Constants.COMMENT_DATA, commentData);

        String commentId = "comment123";
        List<String> userIdList = Arrays.asList("user1"); // Only contains the sender

        // Mock fetchUserFirstName
        when(cacheService.getContentFromCache(anyString())).thenReturn("");
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        helperMethodService.sendNotificationToUser(commentPayload, commentId, userIdList);

        verify(notificationTriggerService, never()).triggerNotification(any(), any(), any(), any(), any(), any());
    }

    // The tests below close branch gaps inside fetchUserFromPrimary's profile-details block.
    // testFetchUserFromPrimary_WithValidData (above) sets PROFILE_IMG/DESIGNATION_KEY/EMPLOYMENT_DETAILS
    // all present-and-non-blank at once, so it only ever exercises the "true" side of each
    // containsKey(...) && isNotBlank/isNotEmpty(...) check. These tests exercise the "false" sides.

    @Test
    void testFetchUserFromPrimary_ProfileDetailsMissingOptionalKeys() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "{\"other\":\"value\"}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        // Non-empty map (so MapUtils.isNotEmpty is true) but none of the three optional
        // keys (PROFILE_IMG, DESIGNATION_KEY, EMPLOYMENT_DETAILS) are present at all.
        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put("other", "value");
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(profileDetailsMap);

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertEquals(1, result.size());
        Map<?, ?> userMap = (Map<?, ?>) result.get(0);
        assertFalse(userMap.containsKey(Constants.PROFILE_IMG_KEY));
        assertFalse(userMap.containsKey(Constants.DESIGNATION_KEY));
        assertFalse(userMap.containsKey(Constants.DEPARTMENT));
    }

    @Test
    void testFetchUserFromPrimary_ProfileDetailsBlankOrEmptyOptionalValues() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS,
                "{\"profileImageUrl\":\"\",\"designation\":\"\",\"employmentDetails\":{}}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        // All three keys present (containsKey == true) but their values are blank/empty,
        // so the isNotBlank/isNotEmpty second half of each condition is false.
        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put(Constants.PROFILE_IMG, "");
        profileDetailsMap.put(Constants.DESIGNATION_KEY, "");
        profileDetailsMap.put(Constants.EMPLOYMENT_DETAILS, new HashMap<>());
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(profileDetailsMap);

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertEquals(1, result.size());
        Map<?, ?> userMap = (Map<?, ?>) result.get(0);
        assertFalse(userMap.containsKey(Constants.PROFILE_IMG_KEY));
        assertFalse(userMap.containsKey(Constants.DESIGNATION_KEY));
        assertFalse(userMap.containsKey(Constants.DEPARTMENT));
    }

    @Test
    void testFetchUserFromPrimary_EmploymentDetailsMissingDepartmentKey() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "{\"employmentDetails\":{\"other\":\"x\"}}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        // employmentDetails map is present and non-empty (so containsKey + isNotEmpty are both
        // true) but it has no DEPARTMENT_KEY entry.
        Map<String, Object> employmentDetails = new HashMap<>();
        employmentDetails.put("other", "x");
        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put(Constants.EMPLOYMENT_DETAILS, employmentDetails);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(profileDetailsMap);

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertEquals(1, result.size());
        Map<?, ?> userMap = (Map<?, ?>) result.get(0);
        assertFalse(userMap.containsKey(Constants.DEPARTMENT));
    }

    @Test
    void testFetchUserFromPrimary_EmploymentDetailsBlankDepartment() throws JsonProcessingException {
        List<String> userIds = Arrays.asList("user1");
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put(Constants.ID, "user1");
        userInfo.put(Constants.FIRST_NAME, "John");
        userInfo.put(Constants.PROFILE_DETAILS, "{\"employmentDetails\":{\"departmentName\":\"\"}}");

        List<Map<String, Object>> userInfoList = Arrays.asList(userInfo);
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(any(), any(), any(), any(), any()))
                .thenReturn(userInfoList);

        // DEPARTMENT_KEY present (containsKey == true) but its value is blank.
        Map<String, Object> employmentDetails = new HashMap<>();
        employmentDetails.put(Constants.DEPARTMENT_KEY, "");
        Map<String, Object> profileDetailsMap = new HashMap<>();
        profileDetailsMap.put(Constants.EMPLOYMENT_DETAILS, employmentDetails);
        when(objectMapper.readValue(anyString(), any(TypeReference.class))).thenReturn(profileDetailsMap);

        List<Object> result = helperMethodService.fetchUserFromPrimary(userIds);

        assertEquals(1, result.size());
        Map<?, ?> userMap = (Map<?, ?>) result.get(0);
        assertFalse(userMap.containsKey(Constants.DEPARTMENT));
    }

    // processMentionedUsers: covers the "!existingMentionedUserIds.contains(userId)" branch
    // when it evaluates to false, i.e. an incoming mentioned user was already mentioned before -
    // it should be kept in the de-duplicated array but NOT reported as newly added.
    @Test
    void testProcessMentionedUsers_IncomingUserAlreadyExisted_notReportedAsNew() {
        ObjectNode data = realObjectMapper.createObjectNode();
        ArrayNode existingUsers = realObjectMapper.createArrayNode();
        ObjectNode existingUser = realObjectMapper.createObjectNode();
        existingUser.put(Constants.USER_ID, "user1");
        existingUsers.add(existingUser);
        data.set(Constants.MENTIONED_USERS, existingUsers);

        ObjectNode updateDataNode = realObjectMapper.createObjectNode();
        ArrayNode incomingUsers = realObjectMapper.createArrayNode();
        ObjectNode sameUser = realObjectMapper.createObjectNode();
        sameUser.put(Constants.USER_ID, "user1");
        incomingUsers.add(sameUser);
        updateDataNode.set(Constants.MENTIONED_USERS, incomingUsers);

        when(objectMapper.createArrayNode()).thenReturn(realObjectMapper.createArrayNode());

        List<String> result = helperMethodService.processMentionedUsers(data, updateDataNode);

        assertTrue(result.isEmpty());
    }
}