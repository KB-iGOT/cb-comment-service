package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplFetchUsersByCommentDataTest {

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CommentServiceImpl commentService;

    private Method fetchUsersByCommentDataMethod;

    @BeforeEach
    void setUp() throws Exception {
        fetchUsersByCommentDataMethod = CommentServiceImpl.class.getDeclaredMethod("fetchUsersByCommentData", List.class);
        fetchUsersByCommentDataMethod.setAccessible(true);
    }

    @Test
    void testFetchUsersByCommentData_Success() throws Exception {
        // Arrange
        List<Comment> comments = createMockComments();
        List<Map<String, Object>> mockUserInfoList = createMockUserInfoList();
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull()))
                .thenReturn(mockUserInfoList);
        
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(createMockProfileDetailsMap());

        // Act
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) fetchUsersByCommentDataMethod.invoke(commentService, comments);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        
        Map<String, Object> firstUser = result.get(0);
        assertEquals("user1", firstUser.get(Constants.USER_ID));
        assertEquals("John", firstUser.get(Constants.USER_NAME));
        assertEquals("profile1.jpg", firstUser.get(Constants.PROFILE_IMG));
        
        verify(cassandraOperation).getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull());
    }

    @Test
    void testFetchUsersByCommentData_WithoutProfileImage() throws Exception {
        // Arrange
        List<Comment> comments = createMockComments();
        List<Map<String, Object>> mockUserInfoList = createMockUserInfoListWithoutProfileImg();
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull()))
                .thenReturn(mockUserInfoList);
        
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(new HashMap<>());

        // Act
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) fetchUsersByCommentDataMethod.invoke(commentService, comments);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        
        Map<String, Object> user = result.get(0);
        assertEquals("user1", user.get(Constants.USER_ID));
        assertEquals("John", user.get(Constants.USER_NAME));
        assertFalse(user.containsKey(Constants.PROFILE_IMG));
    }

    @Test
    void testFetchUsersByCommentData_WithBlankProfileDetails() throws Exception {
        // Arrange
        List<Comment> comments = createMockComments();
        List<Map<String, Object>> mockUserInfoList = createMockUserInfoListWithBlankProfile();
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull()))
                .thenReturn(mockUserInfoList);

        // Act
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) fetchUsersByCommentDataMethod.invoke(commentService, comments);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        
        Map<String, Object> user = result.get(0);
        assertEquals("user1", user.get(Constants.USER_ID));
        assertEquals("John", user.get(Constants.USER_NAME));
        assertFalse(user.containsKey(Constants.PROFILE_IMG));
        
        verify(objectMapper, never()).readValue(anyString(), any(TypeReference.class));
    }

    @Test
    void testFetchUsersByCommentData_JsonProcessingException() throws Exception {
        // Arrange
        List<Comment> comments = createMockComments();
        List<Map<String, Object>> mockUserInfoList = createMockUserInfoList();
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull()))
                .thenReturn(mockUserInfoList);
        
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("JSON parsing error") {});

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            fetchUsersByCommentDataMethod.invoke(commentService, comments);
        });
        
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getCause() instanceof JsonProcessingException);
    }

    @Test
    void testFetchUsersByCommentData_EmptyCommentsList() throws Exception {
        // Arrange
        List<Comment> comments = new ArrayList<>();
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull()))
                .thenReturn(new ArrayList<>());

        // Act
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) fetchUsersByCommentDataMethod.invoke(commentService, comments);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testFetchUsersByCommentData_WithEmptyProfileImageUrl() throws Exception {
        // Arrange
        List<Comment> comments = createMockComments();
        List<Map<String, Object>> mockUserInfoList = createMockUserInfoList();
        
        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(
                eq(Constants.KEYSPACE_SUNBIRD), 
                eq(Constants.TABLE_USER), 
                any(Map.class), 
                eq(Arrays.asList(Constants.PROFILE_DETAILS, Constants.FIRST_NAME, Constants.ID)), 
                isNull()))
                .thenReturn(mockUserInfoList);
        
        Map<String, Object> profileDetailsWithEmptyImg = new HashMap<>();
        profileDetailsWithEmptyImg.put(Constants.PROFILE_IMG, "");
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenReturn(profileDetailsWithEmptyImg);

        // Act
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> result = (List<Map<String, Object>>) fetchUsersByCommentDataMethod.invoke(commentService, comments);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        
        Map<String, Object> firstUser = result.get(0);
        assertEquals("user1", firstUser.get(Constants.USER_ID));
        assertEquals("John", firstUser.get(Constants.USER_NAME));
        assertFalse(firstUser.containsKey(Constants.PROFILE_IMG));
    }

    private List<Comment> createMockComments() {
        List<Comment> comments = new ArrayList<>();
        
        Comment comment1 = new Comment();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentData1 = mapper.createObjectNode();
        ObjectNode commentSource1 = mapper.createObjectNode();
        commentSource1.put(Constants.USER_ID, "user1");
        commentData1.set(Constants.COMMENT_SOURCE, commentSource1);
        comment1.setCommentData(commentData1);
        
        Comment comment2 = new Comment();
        ObjectNode commentData2 = mapper.createObjectNode();
        ObjectNode commentSource2 = mapper.createObjectNode();
        commentSource2.put(Constants.USER_ID, "user2");
        commentData2.set(Constants.COMMENT_SOURCE, commentSource2);
        comment2.setCommentData(commentData2);
        
        comments.add(comment1);
        comments.add(comment2);
        
        return comments;
    }

    private List<Map<String, Object>> createMockUserInfoList() {
        List<Map<String, Object>> userInfoList = new ArrayList<>();
        
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.ID, "user1");
        user1.put(Constants.FIRST_NAME, "John");
        user1.put(Constants.PROFILE_DETAILS, "{\"profileImg\":\"profile1.jpg\"}");
        
        Map<String, Object> user2 = new HashMap<>();
        user2.put(Constants.ID, "user2");
        user2.put(Constants.FIRST_NAME, "Jane");
        user2.put(Constants.PROFILE_DETAILS, "{\"profileImg\":\"profile2.jpg\"}");
        
        userInfoList.add(user1);
        userInfoList.add(user2);
        
        return userInfoList;
    }

    private List<Map<String, Object>> createMockUserInfoListWithoutProfileImg() {
        List<Map<String, Object>> userInfoList = new ArrayList<>();
        
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.ID, "user1");
        user1.put(Constants.FIRST_NAME, "John");
        user1.put(Constants.PROFILE_DETAILS, "{\"otherField\":\"value\"}");
        
        userInfoList.add(user1);
        
        return userInfoList;
    }

    private List<Map<String, Object>> createMockUserInfoListWithBlankProfile() {
        List<Map<String, Object>> userInfoList = new ArrayList<>();
        
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.ID, "user1");
        user1.put(Constants.FIRST_NAME, "John");
        user1.put(Constants.PROFILE_DETAILS, "");
        
        userInfoList.add(user1);
        
        return userInfoList;
    }

    private Map<String, Object> createMockProfileDetailsMap() {
        Map<String, Object> profileDetails = new HashMap<>();
        profileDetails.put(Constants.PROFILE_IMG, "profile1.jpg");
        return profileDetails;
    }
}