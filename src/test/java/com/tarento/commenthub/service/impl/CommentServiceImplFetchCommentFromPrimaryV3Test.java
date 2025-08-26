package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.authentication.util.FetchUserDetails;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentsResoponseDTO;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplFetchCommentFromPrimaryV3Test {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private FetchUserDetails fetchUser;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CommentServiceImpl commentService;

    private Method fetchCommentFromPrimaryV3Method;

    @BeforeEach
    void setUp() throws Exception {
        fetchCommentFromPrimaryV3Method = CommentServiceImpl.class.getDeclaredMethod(
                "fetchCommentFromPrimaryV3", int.class, int.class, List.class, String.class);
        fetchCommentFromPrimaryV3Method.setAccessible(true);
    }

    @Test
    void testFetchCommentFromPrimaryV3_Success() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1", "comment2");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockComments();
        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> userList = createMockUserList();
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList())).thenReturn(userList);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(commentRepository).findByCommentIdIn(eq(childNodeList), any(Pageable.class));
        verify(fetchUser, times(1)).fetchDataForKeys(anyList());
        verify(objectMapper).convertValue(any(CommentsResoponseDTO.class), eq(Map.class));
    }

    @Test
    void testFetchCommentFromPrimaryV3_WithTaggedUsers() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockCommentsWithTaggedUsers();
        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> userList = createMockUserList();
        List<Object> taggedUsers = createMockTaggedUsers();
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList()))
                .thenReturn(userList)
                .thenReturn(taggedUsers);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        verify(fetchUser, times(2)).fetchDataForKeys(anyList());
    }

    @Test
    void testFetchCommentFromPrimaryV3_EmptyComments() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = new ArrayList<>();
        Page<Comment> commentPage = new PageImpl<>(comments);
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        assertEquals(expectedResult, result);
        verify(commentRepository).findByCommentIdIn(eq(childNodeList), any(Pageable.class));
        verify(fetchUser, never()).fetchDataForKeys(anyList());
    }

    @Test
    void testFetchCommentFromPrimaryV3_UserListEmptyFallbackToPrimary() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockComments();
        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> primaryUserList = createMockUserList();
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList()))
                .thenReturn(new ArrayList<>())
                .thenReturn(new ArrayList<>());
        when(fetchUser.fetchUserFromprimary(anyList())).thenReturn(primaryUserList);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        verify(fetchUser, times(1)).fetchUserFromprimary(anyList());
    }

    @Test
    void testFetchCommentFromPrimaryV3_TaggedUsersEmptyFallbackToPrimary() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockCommentsWithTaggedUsers();
        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> userList = createMockUserList();
        List<Object> primaryTaggedUsers = createMockTaggedUsers();
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList()))
                .thenReturn(userList)
                .thenReturn(null);
        when(fetchUser.fetchUserFromprimary(anyList())).thenReturn(primaryTaggedUsers);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        verify(fetchUser).fetchUserFromprimary(anyList());
    }

    @Test
    void testFetchCommentFromPrimaryV3_NoTaggedUsers() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockCommentsWithoutTaggedUsers();
        Page<Comment> commentPage = new PageImpl<>(comments);
        List<Object> userList = createMockUserList();
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(fetchUser.fetchDataForKeys(anyList())).thenReturn(userList);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        verify(fetchUser, times(1)).fetchDataForKeys(anyList());
    }

    @Test
    void testFetchCommentFromPrimaryV3_NullCommentData() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockCommentsWithNullData();
        Page<Comment> commentPage = new PageImpl<>(comments);
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);

        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            fetchCommentFromPrimaryV3Method.invoke(commentService, offset, limit, childNodeList, commentTreeId);
        });
        
        assertTrue(exception.getCause() instanceof NullPointerException);
    }

    @Test
    void testFetchCommentFromPrimaryV3_EmptyUserIdInCommentSource() throws Exception {
        // Arrange
        int offset = 0, limit = 10;
        List<String> childNodeList = Arrays.asList("comment1");
        String commentTreeId = "tree123";
        
        List<Comment> comments = createMockCommentsWithEmptyUserId();
        Page<Comment> commentPage = new PageImpl<>(comments);
        Map<String, Object> expectedResult = new HashMap<>();
        
        when(commentRepository.findByCommentIdIn(eq(childNodeList), any(Pageable.class)))
                .thenReturn(commentPage);
        when(objectMapper.convertValue(any(CommentsResoponseDTO.class), eq(Map.class)))
                .thenReturn(expectedResult);

        // Act
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) fetchCommentFromPrimaryV3Method.invoke(
                commentService, offset, limit, childNodeList, commentTreeId);

        // Assert
        assertNotNull(result);
        verify(fetchUser, never()).fetchDataForKeys(anyList());
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

    private List<Comment> createMockCommentsWithTaggedUsers() {
        List<Comment> comments = new ArrayList<>();
        
        Comment comment1 = new Comment();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentData1 = mapper.createObjectNode();
        ObjectNode commentSource1 = mapper.createObjectNode();
        commentSource1.put(Constants.USER_ID, "user1");
        commentData1.set(Constants.COMMENT_SOURCE, commentSource1);
        commentData1.set(Constants.TAGGED_USERS, mapper.createArrayNode().add("taggedUser1"));
        comment1.setCommentData(commentData1);
        
        comments.add(comment1);
        
        return comments;
    }

    private List<Comment> createMockCommentsWithoutTaggedUsers() {
        List<Comment> comments = new ArrayList<>();
        
        Comment comment1 = new Comment();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentData1 = mapper.createObjectNode();
        ObjectNode commentSource1 = mapper.createObjectNode();
        commentSource1.put(Constants.USER_ID, "user1");
        commentData1.set(Constants.COMMENT_SOURCE, commentSource1);
        comment1.setCommentData(commentData1);
        
        comments.add(comment1);
        
        return comments;
    }

    private List<Comment> createMockCommentsWithNullData() {
        List<Comment> comments = new ArrayList<>();
        
        Comment comment1 = new Comment();
        comment1.setCommentData(null);
        
        comments.add(comment1);
        
        return comments;
    }

    private List<Comment> createMockCommentsWithEmptyUserId() {
        List<Comment> comments = new ArrayList<>();
        
        Comment comment1 = new Comment();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentData1 = mapper.createObjectNode();
        ObjectNode commentSource1 = mapper.createObjectNode();
        commentSource1.put(Constants.USER_ID, "");
        commentData1.set(Constants.COMMENT_SOURCE, commentSource1);
        comment1.setCommentData(commentData1);
        
        comments.add(comment1);
        
        return comments;
    }

    private List<Object> createMockUserList() {
        List<Object> userList = new ArrayList<>();
        Map<String, Object> user1 = new HashMap<>();
        user1.put(Constants.USER_ID, "user1");
        user1.put(Constants.USER_NAME, "John");
        userList.add(user1);
        return userList;
    }

    private List<Object> createMockTaggedUsers() {
        List<Object> taggedUsers = new ArrayList<>();
        Map<String, Object> taggedUser1 = new HashMap<>();
        taggedUser1.put(Constants.USER_ID, "taggedUser1");
        taggedUser1.put(Constants.USER_NAME, "Tagged User");
        taggedUsers.add(taggedUser1);
        return taggedUsers;
    }
}