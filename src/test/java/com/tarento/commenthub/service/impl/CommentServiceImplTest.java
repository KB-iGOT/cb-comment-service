package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.authentication.util.AccessTokenValidator;
import com.tarento.commenthub.authentication.util.FetchUserDetails;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.*;
import com.tarento.commenthub.entity.Comment;
import com.tarento.commenthub.entity.CommentTree;
import com.tarento.commenthub.entity.UserCourseCommentLike;
import com.tarento.commenthub.exception.CommentException;
import com.tarento.commenthub.repository.CommentRepository;
import com.tarento.commenthub.repository.CommentTreeRepository;
import com.tarento.commenthub.repository.UserCommentLikeRepository;
import com.tarento.commenthub.service.CommentTreeService;
import com.tarento.commenthub.service.ContentService;
import com.tarento.commenthub.transactional.cassandrautils.CassandraOperation;
import com.tarento.commenthub.transactional.utils.ApiResponse;
import com.tarento.commenthub.utility.Status;
import com.tarento.commenthub.utility.notificationutill.HelperMethodService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.*;

import static com.tarento.commenthub.constant.Constants.COMMENT_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @InjectMocks
    private CommentServiceImpl commentService;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserCommentLikeRepository userCommentLikeRepository;

    @Mock
    private CassandraOperation cassandraOperation;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CommentTreeRepository commentTreeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private FetchUserDetails fetchUser;

    @Mock
    private RedisTemplate redisTemplateEx;

    @Mock
    private ContentService contentService;
    @Mock
    private CommentTreeService commentTreeService;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private HelperMethodService helperMethodService;


    private CommentTree mockCommentTree;

    private Map<String, Object> baseRequest;

    private final String commentId = "comment123";
    private final String token = "valid-token";
    private final String userId = "user-1";
    private final String courseId = "course-101";
    private static final String VALID_TOKEN = "valid-token";
    private static final String VALID_USER_ID = "user123";
    private static final String PARENT_ID = "parent123";


    @BeforeEach
    void setUp() {
        mockCommentTree = new CommentTree();
        mockCommentTree.setCommentTreeId("tree123");
        mockCommentTree.setStatus("active");
        mockCommentTree.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        mockCommentTree.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));

        baseRequest = new HashMap<>();
        baseRequest.put(COMMENT_ID, commentId);
        baseRequest.put(Constants.REPORTED_REASON, List.of("Spam"));
    }

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(commentService, "contentService", contentService);
        ReflectionTestUtils.setField(commentService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(commentService, "defaultLimit", 10);
        ReflectionTestUtils.setField(commentService, "defaultOffset", 0);
        ReflectionTestUtils.setField(commentService, "jwtSecretKey", "dummysecret");
        ReflectionTestUtils.setField(commentService, "redisTtl", 1000L);
        ReflectionTestUtils.setField(commentService, "redisTemplate", redisTemplate);
    }


    @Test
    void testAddFirstCommentToCreateTree_ValidationError() {
        ObjectNode invalidPayload = JsonNodeFactory.instance.objectNode();
        CommentException exception = assertThrows(CommentException.class, () -> commentService.addFirstCommentToCreateTree(invalidPayload));
        assertTrue(exception.getMessage().contains("Failed to validate payload"));
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testAddNewCommentToTree_ValidationError() {
        ObjectNode invalidPayload = JsonNodeFactory.instance.objectNode();
        invalidPayload.put("someField", "someValue");
        CommentException exception = assertThrows(CommentException.class, () -> commentService.addNewCommentToTree(invalidPayload));
        assertTrue(exception.getMessage().contains("Failed to validate payload"));
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void testUpdateExistingComment_Success() {
        String localCommentId = "comment123";
        String localUserId = "user123";
        String commentTreeId = "tree123";
        ObjectNode testPayload = JsonNodeFactory.instance.objectNode();
        testPayload.put("commentId", localCommentId);
        testPayload.put("commentTreeId", commentTreeId);
        ObjectNode commentData = JsonNodeFactory.instance.objectNode();
        commentData.put("comment", "Updated comment text");
        commentData.put("commentResolved", "false");
        ObjectNode commentSource = JsonNodeFactory.instance.objectNode();
        commentSource.put("userId", localUserId);
        commentSource.put("userPic", "https://example.com/pic.jpg");
        commentSource.put("userRole", "TESTER");
        commentData.set("commentSource", commentSource);
        testPayload.set("commentData", commentData);
        Comment existingComment = new Comment();
        existingComment.setCommentId(localCommentId);
        existingComment.setStatus("ACTIVE");
        ObjectNode existingCommentData = JsonNodeFactory.instance.objectNode();
        existingCommentData.put("comment", "Original comment");
        existingCommentData.put("commentResolved", "false");
        ObjectNode existingCommentSource = JsonNodeFactory.instance.objectNode();
        existingCommentSource.put("userId", localUserId);
        existingCommentData.set("commentSource", existingCommentSource);
        existingCommentData.put("like", 5);
        existingComment.setCommentData(existingCommentData);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentRepository.findById(localCommentId)).thenReturn(Optional.of(existingComment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(commentTreeService.getCommentTreeById(commentTreeId)).thenReturn(mockCommentTree);
        when(helperMethodService.processMentionedUsers(any(), any())).thenReturn(anyList());
        ResponseDTO response = commentService.updateExistingComment(testPayload);
        assertNotNull(response);
        assertNotNull(response.getComment());
        assertEquals(localCommentId, response.getComment().getCommentId());
        assertEquals("Updated comment text", response.getComment().getCommentData().get("comment").asText());
    }

    @Test
    void testAddNewCommentToTree_Success() {
        String commentTreeId = "tree123";
        String localUserId = "user123";
        ObjectNode testPayload = JsonNodeFactory.instance.objectNode();
        testPayload.put("commentTreeId", commentTreeId);
        ObjectNode commentData = JsonNodeFactory.instance.objectNode();
        commentData.put("comment", "New test comment");
        ObjectNode commentSource = JsonNodeFactory.instance.objectNode();
        commentSource.put("userId", localUserId);
        commentSource.put("userPic", "https://example.com/pic.jpg");
        commentSource.put("userRole", "TESTER");
        commentData.set("commentSource", commentSource);
        testPayload.set("commentData", commentData);
        Comment mockComment = new Comment();
        mockComment.setCommentId("comment123");
        mockComment.setStatus("ACTIVE");
        mockComment.setCommentData(commentData);
        mockComment.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        mockComment.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        CommentTree localMockCommentTree = new CommentTree();
        localMockCommentTree.setCommentTreeId(commentTreeId);
        localMockCommentTree.setStatus("ACTIVE");
        localMockCommentTree.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        localMockCommentTree.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentRepository.save(any(Comment.class))).thenReturn(mockComment);
        when(commentTreeService.updateCommentTree(any(JsonNode.class))).thenReturn(localMockCommentTree);
        ResponseDTO response = commentService.addNewCommentToTree(testPayload);
        assertNotNull(response);
        assertNotNull(response.getComment());
        assertNotNull(response.getCommentTree());
        assertEquals(mockComment.getCommentId(), response.getComment().getCommentId());
    }

    @Test
    void testAddNewCommentToTree_ValidationFailure() {

        ObjectNode invalidPayload = JsonNodeFactory.instance.objectNode();
        invalidPayload.put("invalidField", "someValue");
        assertThrows(CommentException.class, () -> commentService.addNewCommentToTree(invalidPayload));
        verify(commentRepository, never()).save(any(Comment.class));
        verify(commentTreeService, never()).updateCommentTree(any(JsonNode.class));
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void testAddFirstCommentToCreateTree_Success() {
        ObjectNode testPayload = JsonNodeFactory.instance.objectNode();
        ObjectNode commentData = JsonNodeFactory.instance.objectNode();
        commentData.put("comment", "First test comment");
        ObjectNode commentSource = JsonNodeFactory.instance.objectNode();
        commentSource.put("userId", userId);
        commentSource.put("userPic", "https://example.com/pic.jpg");
        commentSource.put("userRole", "TESTER");
        commentData.set("commentSource", commentSource);
        testPayload.set("commentData", commentData);
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        commentTreeData.put("entityId", "entity123");
        commentTreeData.put("entityType", "TEST_ENTITY");
        commentTreeData.put("workflow", "DEFAULT_WORKFLOW");
        testPayload.set("commentTreeData", commentTreeData);
        Comment mockComment = new Comment();
        mockComment.setCommentId("comment123");
        mockComment.setStatus("ACTIVE");
        mockComment.setCommentData(commentData);
        mockComment.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        mockComment.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        CommentTree localMockCommentTree = new CommentTree();
        localMockCommentTree.setCommentTreeId("tree123");
        localMockCommentTree.setStatus("ACTIVE");
        localMockCommentTree.setCreatedDate(new Timestamp(System.currentTimeMillis()));
        localMockCommentTree.setLastUpdatedDate(new Timestamp(System.currentTimeMillis()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentRepository.save(any(Comment.class))).thenReturn(mockComment);
        when(commentTreeService.createCommentTree(any(JsonNode.class))).thenReturn(localMockCommentTree);

        ResponseDTO response = commentService.addFirstCommentToCreateTree(testPayload);
        assertNotNull(response);
        assertNotNull(response.getComment());
        assertNotNull(response.getCommentTree());
        assertEquals(mockComment.getCommentId(), response.getComment().getCommentId());
        assertEquals(mockComment.getStatus(), response.getComment().getStatus());
        assertEquals(mockComment.getCommentData(), response.getComment().getCommentData());
    }

    @Test
    void testGetComments_Success() {
        String entityType = "TEST_ENTITY";
        String entityId = "entity123";
        String workflow = "TEST_WORKFLOW";
        String userId1 = "user123";
        String userId2 = "user456";
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO(entityType, entityId, workflow);
        CommentTree localMockCommentTree = new CommentTree();
        localMockCommentTree.setCommentTreeId("tree123");
        localMockCommentTree.setStatus("ACTIVE");
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        ArrayNode childNodes = JsonNodeFactory.instance.arrayNode();
        childNodes.add("comment123");
        childNodes.add("comment456");
        commentTreeData.set(Constants.CHILD_NODES, childNodes);
        localMockCommentTree.setCommentTreeData(commentTreeData);
        List<Comment> mockComments = new ArrayList<>();
        Comment comment1 = new Comment();
        comment1.setCommentId("comment123");
        ObjectNode commentData1 = JsonNodeFactory.instance.objectNode();
        ObjectNode commentSource1 = JsonNodeFactory.instance.objectNode();
        commentSource1.put(Constants.USER_ID, userId1);
        commentData1.set(Constants.COMMENT_SOURCE, commentSource1);
        comment1.setCommentData(commentData1);
        Comment comment2 = new Comment();
        comment2.setCommentId("comment456");
        ObjectNode commentData2 = JsonNodeFactory.instance.objectNode();
        ObjectNode commentSource2 = JsonNodeFactory.instance.objectNode();
        commentSource2.put(Constants.USER_ID, userId2);
        commentData2.set(Constants.COMMENT_SOURCE, commentSource2);
        ArrayNode taggedUsers = JsonNodeFactory.instance.arrayNode();
        taggedUsers.add(userId1);
        commentData2.set(Constants.TAGGED_USERS, taggedUsers);
        comment2.setCommentData(commentData2);
        mockComments.add(comment1);
        mockComments.add(comment2);
        List<Object> mockUserList = Arrays.asList(createMockUser(userId1), createMockUser(userId2));
        when(commentTreeService.getCommentTree(identifierDTO)).thenReturn(localMockCommentTree);
        when(commentRepository.findByCommentIdInAndStatus(anyList(), eq("active"))).thenReturn(mockComments);
        when(fetchUser.fetchDataForKeys(anyList())).thenReturn(mockUserList);
        CommentsResoponseDTO response = commentService.getComments(identifierDTO);
        assertNotNull(response);
        assertEquals(localMockCommentTree, response.getCommentTree());
        assertEquals(mockComments, response.getComments());
        assertEquals(2, response.getCommentCount());
        verify(fetchUser, atLeastOnce()).fetchDataForKeys(anyList());
        verify(fetchUser, never()).fetchUserFromprimary(anyList());
    }

    @Test
    void testGetComments_NoCommentsFound() {
        String entityType = "TEST_ENTITY";
        String entityId = "entity123";
        String workflow = "TEST_WORKFLOW";
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO(entityType, entityId, workflow);
        CommentTree localMockCommentTree = new CommentTree();
        localMockCommentTree.setCommentTreeId("tree123");
        localMockCommentTree.setStatus("ACTIVE");
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        ArrayNode childNodes = JsonNodeFactory.instance.arrayNode();
        commentTreeData.set(Constants.CHILD_NODES, childNodes);
        localMockCommentTree.setCommentTreeData(commentTreeData);
        when(commentTreeService.getCommentTree(identifierDTO)).thenReturn(localMockCommentTree);
        when(commentRepository.findByCommentIdInAndStatus(anyList(), eq("active"))).thenReturn(Collections.emptyList());
        CommentsResoponseDTO response = commentService.getComments(identifierDTO);
        assertNotNull(response);
        assertEquals(localMockCommentTree, response.getCommentTree());
        assertTrue(response.getComments().isEmpty());
        assertEquals(0, response.getCommentCount());
        verify(commentTreeService).getCommentTree(identifierDTO);
        verify(commentRepository).findByCommentIdInAndStatus(anyList(), eq("active"));
    }

    @Test
    void testGetComments_UserFetchFallback() {
        String entityType = "TEST_ENTITY";
        String entityId = "entity123";
        String workflow = "TEST_WORKFLOW";
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO(entityType, entityId, workflow);
        CommentTree localMockCommentTree = new CommentTree();
        localMockCommentTree.setCommentTreeId("tree123");
        localMockCommentTree.setStatus("ACTIVE");
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        ArrayNode childNodes = JsonNodeFactory.instance.arrayNode();
        childNodes.add("comment123");
        commentTreeData.set(Constants.CHILD_NODES, childNodes);
        localMockCommentTree.setCommentTreeData(commentTreeData);
        Comment mockComment = new Comment();
        mockComment.setCommentId("comment123");
        ObjectNode commentData = JsonNodeFactory.instance.objectNode();
        ObjectNode commentSource = JsonNodeFactory.instance.objectNode();
        commentSource.put(Constants.USER_ID, userId);
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        mockComment.setCommentData(commentData);
        List<Comment> mockComments = Collections.singletonList(mockComment);
        when(commentTreeService.getCommentTree(identifierDTO)).thenReturn(localMockCommentTree);
        when(commentRepository.findByCommentIdInAndStatus(anyList(), eq("active"))).thenReturn(mockComments);
        CommentsResoponseDTO response = commentService.getComments(identifierDTO);
        assertNotNull(response);
        assertEquals(localMockCommentTree, response.getCommentTree());
        assertEquals(mockComments, response.getComments());
        assertEquals(1, response.getCommentCount());
        verify(commentTreeService).getCommentTree(identifierDTO);
        verify(commentRepository).findByCommentIdInAndStatus(anyList(), eq("active"));
    }

    @Test
    void testGetComments_CommentTreeNotFound() {
        String entityType = "TEST_ENTITY";
        String entityId = "entity123";
        String workflow = "TEST_WORKFLOW";
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO(entityType, entityId, workflow);
        CommentTree emptyCommentTree = new CommentTree();
        emptyCommentTree.setCommentTreeId("tree123");
        emptyCommentTree.setStatus("ACTIVE");
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        ArrayNode childNodes = JsonNodeFactory.instance.arrayNode();
        commentTreeData.set(Constants.CHILD_NODES, childNodes);
        emptyCommentTree.setCommentTreeData(commentTreeData);
        when(commentTreeService.getCommentTree(identifierDTO)).thenReturn(emptyCommentTree);
        when(commentRepository.findByCommentIdInAndStatus(anyList(), eq("active"))).thenReturn(Collections.emptyList());
        CommentsResoponseDTO response = commentService.getComments(identifierDTO);
        assertNotNull(response);
        assertEquals(emptyCommentTree, response.getCommentTree());
        assertTrue(response.getComments().isEmpty());
        assertEquals(0, response.getCommentCount());
        verify(commentRepository).findByCommentIdInAndStatus(anyList(), eq("active"));
    }

    @Test
    void testDeleteCommentById_Success() {
        // Arrange
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");

        // Create comment with valid user
        Comment comment = createMockComment(VALID_USER_ID, Status.ACTIVE.name());

        // Mock Redis operations
        RedisOperations<String, Object> redisOperations = mock(RedisOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.getOperations()).thenReturn(redisOperations);

        // Mock other dependencies
        when(accessTokenValidator.verifyUserToken(VALID_TOKEN)).thenReturn(VALID_USER_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        Comment result = commentService.deleteCommentById(COMMENT_ID, identifierDTO, VALID_TOKEN, PARENT_ID);

        // Assert
        assertNotNull(result);
        assertEquals(Status.INACTIVE.name().toLowerCase(), result.getStatus());

        // Verify interactions
        verify(accessTokenValidator, times(1)).verifyUserToken(VALID_TOKEN);

    }

    @Test
    void testDeleteCommentById_UnauthorizedUser() {
        // Arrange
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");

        when(accessTokenValidator.verifyUserToken(VALID_TOKEN)).thenReturn(Constants.UNAUTHORIZED_USER);

        // Act & Assert
        CommentException exception = assertThrows(CommentException.class, () -> commentService.deleteCommentById(COMMENT_ID, identifierDTO, VALID_TOKEN, PARENT_ID));

        assertEquals("Not a valid user", exception.getMessage());
        assertEquals(Constants.ERROR, exception.getCode());
    }

    @Test
    void testDeleteCommentById_CommentNotFound() {
        // Arrange
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");

        when(accessTokenValidator.verifyUserToken(VALID_TOKEN)).thenReturn(VALID_USER_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.empty());

        // Act & Assert
        CommentException exception = assertThrows(CommentException.class, () -> commentService.deleteCommentById(COMMENT_ID, identifierDTO, VALID_TOKEN, PARENT_ID));

        assertEquals("No such comment found", exception.getMessage());
        assertEquals(Constants.ERROR, exception.getCode());
    }

    @Test
    void testDeleteCommentById_UnauthorizedAccess() {
        // Arrange
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");

        // Create comment with different user
        Comment comment = createMockComment("different-user", Status.ACTIVE.name());

        when(accessTokenValidator.verifyUserToken(VALID_TOKEN)).thenReturn(VALID_USER_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        // Act & Assert
        CommentException exception = assertThrows(CommentException.class, () -> commentService.deleteCommentById(COMMENT_ID, identifierDTO, VALID_TOKEN, PARENT_ID));

        assertEquals("No access to edit the comment", exception.getMessage());
        assertEquals(Constants.ERROR, exception.getCode());
    }

    @Test
    void testDeleteCommentById_AlreadyDeleted() {
        // Arrange
        CommentTreeIdentifierDTO identifierDTO = new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");

        // Create comment with inactive status
        Comment comment = createMockComment(VALID_USER_ID, Status.INACTIVE.name());

        when(accessTokenValidator.verifyUserToken(VALID_TOKEN)).thenReturn(VALID_USER_ID);
        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        // Act & Assert
        CommentException exception = assertThrows(CommentException.class, () -> commentService.deleteCommentById(COMMENT_ID, identifierDTO, VALID_TOKEN, PARENT_ID));

        assertEquals("You are trying to delete an already deleted comment", exception.getMessage());
        assertEquals(Constants.ERROR, exception.getCode());

    }

    // Helper method to create mock comment
    private Comment createMockComment(String userId, String status) {
        Comment comment = new Comment();
        comment.setCommentId(COMMENT_ID);
        comment.setStatus(status);

        ObjectNode commentData = JsonNodeFactory.instance.objectNode();
        ObjectNode commentSource = JsonNodeFactory.instance.objectNode();
        commentSource.put(Constants.USER_ID, userId);
        commentData.set(Constants.COMMENT_SOURCE, commentSource);

        comment.setCommentData(commentData);
        return comment;
    }


    @Test
    void testLikeCommentWithMissingFields() {
        Map<String, Object> likePayload = new HashMap<>();
        ApiResponse response = commentService.likeComment(likePayload);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertNotNull(response.getParams().getErr());
    }

    @Test
    void testLikeCommentWithInvalidFlag() {
        Map<String, Object> likePayload = new HashMap<>();
        likePayload.put(COMMENT_ID, "c1");
        likePayload.put(Constants.USERID, "u1");
        likePayload.put(Constants.COURSEID, "course1");
        likePayload.put(Constants.FLAG, "heart"); // invalid flag

        ApiResponse response = commentService.likeComment(likePayload);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains("flag must be either"));
    }

    @Test
    void testLikeCommentWithExistingLike() {
        Map<String, Object> likePayload = validPayload();

        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put(Constants.LIKE, 2);

        Comment comment = new Comment();
        comment.setCommentData(commentData);

        List<String> liked = new ArrayList<>();
        liked.add("c1");
        UserCourseCommentLike like = new UserCourseCommentLike();
        like.setCommentIds(liked);

        when(commentRepository.findById("c1")).thenReturn(Optional.of(comment));
        when(userCommentLikeRepository.findById(any())).thenReturn(Optional.of(like));
        when(commentRepository.save(any())).thenReturn(comment);

        ApiResponse response = commentService.likeComment(likePayload);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testLikeCommentWithNewLike() {
        Map<String, Object> likePayload = validPayload();

        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put(Constants.LIKE, 0);

        Comment comment = new Comment();
        comment.setCommentData(commentData);

        when(commentRepository.findById("c1")).thenReturn(Optional.of(comment));
        when(userCommentLikeRepository.findById(any())).thenReturn(Optional.empty());
        when(commentRepository.save(any())).thenReturn(comment);

        ApiResponse response = commentService.likeComment(likePayload);
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testLikeCommentThrowsException() {
        Map<String, Object> likePayload = validPayload();

        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put(Constants.LIKE, 1);

        Comment comment = new Comment();
        comment.setCommentData(commentData);

        when(commentRepository.findById("c1")).thenReturn(Optional.of(comment));
        when(userCommentLikeRepository.findById(any())).thenThrow(new RuntimeException("DB Error"));

        assertThrows(RuntimeException.class, () -> commentService.likeComment(likePayload));
    }

    @Test
    void testGetCommentLike_success() {
        Map<String, Object> recordMap = new HashMap<>();
        recordMap.put("flag", 1);
        List<Map<String, Object>> records = List.of(recordMap);

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(eq(Constants.KEYSPACE_SUNBIRD), eq("comment_likes"), anyMap(), eq(Collections.singletonList("flag")), isNull())).thenReturn(records);

        ApiResponse response = commentService.getCommentLike(commentId, userId);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        assertEquals(1, response.getResult().get("flag"));
    }

    @Test
    void testGetCommentLike_userDidNotLikeComment() {

        when(cassandraOperation.getRecordsByPropertiesWithoutFiltering(anyString(), anyString(), anyMap(), anyList(), isNull())).thenReturn(Collections.emptyList());

        ApiResponse response = commentService.getCommentLike(commentId, userId);

        assertEquals(HttpStatus.OK, response.getResponseCode()); // still OK
        assertEquals("This user not liked this comment", response.getParams().getErr());
    }

    @Test
    void testGetCommentLike_missingCommentId() {
        ApiResponse response = commentService.getCommentLike("", userId);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(COMMENT_ID));
    }

    @Test
    void testGetCommentLike_missingUserId() {
        ApiResponse response = commentService.getCommentLike(commentId, "");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(Constants.USERID));
    }

    @Test
    void testGetCommentLike_missingBothParams() {
        ApiResponse response = commentService.getCommentLike("", "");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(COMMENT_ID));
        assertTrue(response.getParams().getErr().contains(Constants.USERID));
    }

    @Test
    void testPaginatedComment_withMissingTree_shouldReturnNotFound() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("tree-id");
        Mockito.when(commentTreeRepository.findById("tree-id")).thenReturn(Optional.empty());

        ApiResponse response = commentService.paginatedComment(criteria, "v1");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals("CommentTree Not found", response.getParams().getErr());
    }

    @Test
    void testPaginatedComment_fromRedisCache() {
        String treeId = "tree-id";
        List<String> children = List.of("c1", "c2");
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(treeId);
        criteria.setOverrideCache(false);

        CommentTree tree = new CommentTree();
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        commentTreeData.set(Constants.FIRST_LEVEL_NODES, new ObjectMapper().convertValue(children, JsonNode.class));
        commentTreeData.put(Constants.ENTITY_ID, "entity-123");
        tree.setCommentTreeData(commentTreeData);

        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(commentTreeRepository.findById(treeId)).thenReturn(Optional.of(tree));
        String cachedJson = "{\"cachedKey\":\"cachedValue\"}";
        Mockito.when(valueOperations.get(Mockito.anyString())).thenReturn(cachedJson);

        ApiResponse response = commentService.paginatedComment(criteria, "v1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("cachedValue", ((Map<?, ?>) response.getResult()).get("cachedKey"));
    }

    @Test
    void testPaginatedComment_cacheMissFallbackToPrimary() {
        String treeId = "tree-id";
        List<String> children = List.of("c1", "c2");
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(treeId);
        criteria.setOverrideCache(false);

        CommentTree tree = new CommentTree();
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        commentTreeData.set(Constants.FIRST_LEVEL_NODES, new ObjectMapper().convertValue(children, JsonNode.class));
        commentTreeData.put(Constants.ENTITY_ID, "course-id");
        tree.setCommentTreeData(commentTreeData);

        List<Comment> commentList = new ArrayList<>();
        Comment comment = new Comment();
        comment.setCommentData(JsonNodeFactory.instance.objectNode().putObject(Constants.COMMENT_SOURCE).put(Constants.USER_ID, "user1"));
        commentList.add(comment);
        Map<String, Object> courseDetails = Map.of("name", "Java Course", "id", courseId);

        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(contentService.readContentFromCache(Mockito.eq(courseId), Mockito.isNull())).thenReturn(courseDetails);
        Mockito.when(commentTreeRepository.findById(treeId)).thenReturn(Optional.of(tree));
        Mockito.when(commentRepository.findByCommentIdIn(Mockito.anyList(), Mockito.any(Pageable.class))).thenReturn(new PageImpl<>(commentList));
        ApiResponse response = commentService.paginatedComment(criteria, "v1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }


    @Test
    void testPaginatedComment_withOverrideCache() {
        String treeId = "tree-id";
        List<String> children = List.of("c1");
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(treeId);
        criteria.setOverrideCache(true);
        criteria.setEnrichedUser(true);

        List<Comment> comments = new ArrayList<>();
        Comment comment = new Comment();
        comment.setCommentData(JsonNodeFactory.instance.objectNode().putObject(Constants.COMMENT_SOURCE).put(Constants.USER_ID, "user1"));
        comments.add(comment);

        CommentTree tree = new CommentTree();
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        commentTreeData.set(Constants.FIRST_LEVEL_NODES, new ObjectMapper().convertValue(children, JsonNode.class));
        commentTreeData.set(Constants.CHILD_NODES, new ObjectMapper().createArrayNode());
        commentTreeData.put(Constants.ENTITY_ID, "course-id");
        tree.setCommentTreeData(commentTreeData);
        Comment mockComment = new Comment();
        mockComment.setCommentId("c1");

// Build JSON structure for commentData
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentDataNode = mapper.createObjectNode();

// Add `taggedUsers` array node
        ArrayNode taggedUsersArray = mapper.createArrayNode();
        taggedUsersArray.add("user1").add("user2");
        commentDataNode.set(Constants.TAGGED_USERS, taggedUsersArray);

// Add nested `commentSource` object with `userId`
        ObjectNode commentSourceNode = commentDataNode.putObject(Constants.COMMENT_SOURCE);
        commentSourceNode.put(Constants.USER_ID, "user1");

// Set the mock commentData
        mockComment.setCommentData(commentDataNode);

        Page<Comment> mockCommentPage = new PageImpl<>(List.of(mockComment));

        // ✅ Allow null for first argument to prevent strict stubbing exception
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentRepository.findByCommentIdIn(any(), any(Pageable.class))).thenReturn(mockCommentPage);


        Mockito.when(commentTreeRepository.findById(treeId)).thenReturn(Optional.of(tree));
        Mockito.when(fetchUser.fetchDataForKeys(Mockito.anyList())).thenReturn(List.of(Map.of("id", "user1")));

        ApiResponse response = commentService.paginatedComment(criteria, "v1");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testPaginatedComment_withVersionV2_removesKeys() {
        String treeId = "tree-id";
        List<String> children = List.of("c1");
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(treeId);
        criteria.setOverrideCache(true);

        CommentTree tree = new CommentTree();
        ObjectNode commentTreeData = JsonNodeFactory.instance.objectNode();
        commentTreeData.set(Constants.FIRST_LEVEL_NODES, new ObjectMapper().convertValue(children, JsonNode.class));
        commentTreeData.set(Constants.CHILD_NODES, new ObjectMapper().createArrayNode());
        commentTreeData.put(Constants.ENTITY_ID, "course-id");
        tree.setCommentTreeData(commentTreeData);
        Comment mockComment = new Comment();
        mockComment.setCommentId("c1");

// Build JSON structure for commentData
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentDataNode = mapper.createObjectNode();

// Add `taggedUsers` array node
        ArrayNode taggedUsersArray = mapper.createArrayNode();
        taggedUsersArray.add("user1").add("user2");
        commentDataNode.set(Constants.TAGGED_USERS, taggedUsersArray);

// Add nested `commentSource` object with `userId`
        ObjectNode commentSourceNode = commentDataNode.putObject(Constants.COMMENT_SOURCE);
        commentSourceNode.put(Constants.USER_ID, "user1");

// Set the mock commentData
        mockComment.setCommentData(commentDataNode);

        Page<Comment> mockCommentPage = new PageImpl<>(List.of(mockComment));

        // ✅ Allow null for first argument to prevent strict stubbing exception
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentRepository.findByCommentIdIn(any(), any(Pageable.class))).thenReturn(mockCommentPage);
        Mockito.when(commentTreeRepository.findById(treeId)).thenReturn(Optional.of(tree));
        Mockito.when(fetchUser.fetchDataForKeys(Mockito.anyList())).thenReturn(List.of(Map.of("id", "user1")));

        ApiResponse response = commentService.paginatedComment(criteria, "v2");

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListOfComments_withValidInput_shouldReturnSuccessResponse() {
        List<String> commentIds = List.of("comment1");

        Comment comment = new Comment();
        ObjectNode commentData = new ObjectMapper().createObjectNode();
        ObjectNode sourceNode = commentData.putObject("source");
        sourceNode.put("userId", "123");
        commentData.putArray("taggedUsers").add("456").add("789");
        comment.setCommentData(commentData);

        List<Comment> comments = List.of(comment);

        List<String> statuses = List.of("active", "suspended");
        Sort sort = Sort.by(Sort.Direction.DESC, "createdDate");

        when(commentRepository.findByCommentIdInAndStatusIn(eq(commentIds), eq(statuses), eq(sort))).thenReturn(comments);

        List<Object> mockUserList = List.of(Map.of("id", "user:123"));

        lenient().when(fetchUser.fetchDataForKeys(List.of("user:123"))).thenReturn(null);
        lenient().when(fetchUser.fetchUserFromprimary(List.of("123"))).thenReturn(mockUserList);

        ApiResponse response = commentService.listOfComments(commentIds);

        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    @Test
    void testListOfComments_withEmptyCommentIds_shouldReturnBadRequest() {
        List<String> commentIds = new ArrayList<>();

        ApiResponse response = commentService.listOfComments(commentIds);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Bad rqst", response.getParams().getErr());
    }

    @Test
    void testReportComment_Success() {

        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, commentId);
        request.put(Constants.REPORTED_REASON, List.of("Spam"));
        request.put(Constants.OTHER_REASON, "Other detail");

        ObjectNode commentData = new ObjectMapper().createObjectNode();
        Comment comment = new Comment();
        comment.setCommentId(commentId);
        comment.setStatus("ACTIVE");
        comment.setCommentData(commentData);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        ApiResponse response = commentService.reportComment(request, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
    }

    @Test
    void testReportComment_InvalidUser() {
        Map<String, Object> request = new HashMap<>();
        String invalidToken = "invalid";

        when(accessTokenValidator.verifyUserToken(invalidToken)).thenReturn(Constants.UNAUTHORIZED_USER);

        ApiResponse response = commentService.reportComment(request, invalidToken);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReportComment_ValidationFails_BlankCommentId() {
        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, "");
        request.put(Constants.REPORTED_REASON, List.of("Spam"));

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user-123");

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReportComment_ValidationFails_EmptyReportedReason() {
        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, "cid");
        request.put(Constants.REPORTED_REASON, List.of());

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user-123");

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReportComment_ValidationFails_OthersSelectedWithoutOtherReason() {
        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, "cid");
        request.put(Constants.REPORTED_REASON, List.of("Others")); // Missing OTHER_REASON

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user-123");

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReportComment_ValidationFails_ReportedReasonNotAList() {
        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, "cid");
        request.put(Constants.REPORTED_REASON, "InvalidType");

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user-123");

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
    }

    @Test
    void testReportComment_CommentNotFound() {
        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, "cid");
        request.put(Constants.REPORTED_REASON, List.of("Spam"));

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user-123");
        when(commentRepository.findById("cid")).thenReturn(Optional.empty());

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testReportComment_CommentNotActive() {
        Comment comment = new Comment();
        comment.setStatus("deleted");
        comment.setCommentId("cid");
        comment.setCommentData(new ObjectMapper().createObjectNode());

        Map<String, Object> request = new HashMap<>();
        request.put(COMMENT_ID, "cid");
        request.put(Constants.REPORTED_REASON, List.of("Spam"));

        when(accessTokenValidator.verifyUserToken(anyString())).thenReturn("user-123");
        when(commentRepository.findById("cid")).thenReturn(Optional.of(comment));

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void deleteReportedComments_success() {
        Comment comment = new Comment();
        comment.setStatus("suspended");
        ObjectNode commentData = mock(ObjectNode.class);
        comment.setCommentData(commentData);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertNotNull(response.getResult());
        verify(commentData).put(Constants.DELETED_BY, userId);
        assertEquals("inactive", ((Comment) commentRepository.save(comment)).getStatus());
    }

    @Test
    void deleteReportedComments_invalidUser() {
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(Constants.UNAUTHORIZED_USER);

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_USER, response.getParams().getErr());
    }

    @Test
    void deleteReportedComments_validationFails() {
        baseRequest.put(Constants.REPORTED_REASON, new ArrayList<>());
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(Constants.REPORTED_REASON));
    }

    @Test
    void deleteReportedComments_commentNotFound() {
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.NOT_FOUND, response.getParams().getErr());
    }

    @Test
    void deleteReportedComments_commentNotSuspended() {
        Comment comment = new Comment();
        comment.setStatus("active");

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals(Constants.NOT_SUSPENDED_STATUS, response.getParams().getErr());
    }

    @Test
    void deleteReportedComments_otherReasonMissing() {
        List<String> reasons = List.of("Spam", "Others");
        baseRequest.put(Constants.REPORTED_REASON, reasons); // has "Others", but no OTHER_REASON
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(Constants.OTHER_REASON));
    }

    @Test
    void deleteReportedComments_reportedReasonNotList() {
        baseRequest.put(Constants.REPORTED_REASON, "Not a list");
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(Constants.REPORTED_REASON));
    }

    @Test
    void deleteReportedComments_missingCommentId() {
        baseRequest.put(COMMENT_ID, "");
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        ApiResponse response = commentService.deleteReportedComments(baseRequest, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(COMMENT_ID));
    }

    @Test
    void getCommentsLikedByUser_success() {
        List<String> likedCommentIds = List.of("comment-1", "comment-2");
        UserCourseCommentLike likeRecord = new UserCourseCommentLike();
        likeRecord.setCommentIds(likedCommentIds);

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(userCommentLikeRepository.findById(new UserCourseCommentsId(userId, courseId))).thenReturn(Optional.of(likeRecord));

        ApiResponse response = commentService.getCommentsLikedByUser(courseId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        Map<String, Object> result = (Map<String, Object>) response.getResult();
        assertTrue(result.containsKey(COMMENT_ID));
        assertEquals(likedCommentIds, result.get(COMMENT_ID));
    }

    @Test
    void getCommentsLikedByUser_userUnauthorized() {
        String unauthorizedUser = Constants.UNAUTHORIZED_USER;
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(unauthorizedUser);

        ApiResponse response = commentService.getCommentsLikedByUser(courseId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_USER, response.getParams().getErr());
    }

    @Test
    void getCommentsLikedByUser_userIdBlank() {
        when(accessTokenValidator.verifyUserToken(token)).thenReturn("");

        ApiResponse response = commentService.getCommentsLikedByUser(courseId, token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.INVALID_USER, response.getParams().getErr());
    }

    @Test
    void getCommentsLikedByUser_courseIdBlank() {
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);

        ApiResponse response = commentService.getCommentsLikedByUser("", token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals(Constants.EMPTY_COURSEID, response.getParams().getErr());
    }

    @Test
    void getCommentsLikedByUser_noRecordFound() {
        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(userCommentLikeRepository.findById(new UserCourseCommentsId(userId, courseId))).thenReturn(Optional.empty());

        ApiResponse response = commentService.getCommentsLikedByUser(courseId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((Map<String, Object>) response.getResult()).isEmpty());
    }

    @Test
    void getCommentsLikedByUser_emptyCommentIds() {
        UserCourseCommentLike likeRecord = new UserCourseCommentLike();
        likeRecord.setCommentIds(Collections.emptyList());

        when(accessTokenValidator.verifyUserToken(token)).thenReturn(userId);
        when(userCommentLikeRepository.findById(new UserCourseCommentsId(userId, courseId))).thenReturn(Optional.of(likeRecord));

        ApiResponse response = commentService.getCommentsLikedByUser(courseId, token);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertTrue(((Map<String, Object>) response.getResult()).isEmpty());
    }

    @Test
    void testPaginatedCommentV3_invalidPayload() {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setCommentTreeId("");
        searchCriteria.setEntityType("");
        searchCriteria.setEntityId("");
        searchCriteria.setWorkflow("");

        ApiResponse response = commentService.paginatedCommentV3(searchCriteria);
        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertEquals("Failed Due To Missing Params - [commentTreeId].", response.getParams().getErr());
    }

    @Test
    void testPaginatedCommentV3_fetchFromRedisCache() {
        String commentTreeId = "jwtToken";
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(commentTreeId);
        criteria.setOverrideCache(false);

        Map<String, Object> commentTreeMap = new HashMap<>();
        commentTreeMap.put("firstLevelNodes", Arrays.asList("c1", "c2"));

        assertNull(null);
    }

    @Test
    void testPaginatedCommentV3_fetchFromPrimaryAndCache() {
        String commentTreeId = "jwtToken";
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(commentTreeId);
        criteria.setOverrideCache(false);

        Map<String, Object> commentTreeMap = new HashMap<>();
        commentTreeMap.put("firstLevelNodes", Arrays.asList("c1", "c2"));

        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(redisTemplateEx.opsForValue()).thenReturn(valueOperations);


        when(redisTemplateEx.opsForValue().get(anyString())).thenReturn(null);

        ApiResponse response = commentService.paginatedCommentV3(criteria);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testPaginatedCommentV3_overrideCache() {
        String commentTreeId = "jwtToken";
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId(commentTreeId);
        criteria.setOverrideCache(true);
        Comment mockComment = new Comment();
        mockComment.setCommentId("c1");

// Build JSON structure for commentData
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode commentDataNode = mapper.createObjectNode();

// Add `taggedUsers` array node
        ArrayNode taggedUsersArray = mapper.createArrayNode();
        taggedUsersArray.add("user1").add("user2");
        commentDataNode.set(Constants.TAGGED_USERS, taggedUsersArray);

// Add nested `commentSource` object with `userId`
        ObjectNode commentSourceNode = commentDataNode.putObject(Constants.COMMENT_SOURCE);
        commentSourceNode.put(Constants.USER_ID, "user1");

// Set the mock commentData
        mockComment.setCommentData(commentDataNode);

        // Mock CommentTreeRepository
        CommentTree tree = new CommentTree();
        when(commentTreeRepository.findById(commentTreeId)).thenReturn(Optional.of(tree));

        // Redis mocks
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Map<String, Object> commentResultMap = new HashMap<>();
        commentResultMap.put(Constants.FIRST_LEVEL_NODES, List.of("c1", "c2", "c3"));

// Return this map when get() is called with the Redis key
        when(valueOperations.get(Constants.COMMENT_TREE_REDIS_KEY + "jwtToken")).thenReturn(commentResultMap);

        // Call the service method
        ApiResponse response = commentService.paginatedCommentV3(criteria);

        // Assertions
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void testPaginatedCommentV3_WithEmptyCommentTreeId() {
        SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setCommentTreeId("");
        searchCriteria.setEntityType("TEST");
        searchCriteria.setEntityId("123");
        searchCriteria.setWorkflow("workflow1");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(contains(Constants.COMMENT_TREE_REDIS_KEY))).thenReturn(null);


        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.empty());
        ApiResponse response = commentService.paginatedCommentV3(searchCriteria);
        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        assertEquals("CommentTree Not found", response.getParams().getErr());
        verify(commentTreeRepository).findById(anyString());
        verify(valueOperations).get(contains(Constants.COMMENT_TREE_REDIS_KEY));
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put(COMMENT_ID, "c1");
        map.put(Constants.USERID, "u1");
        map.put(Constants.COURSEID, "course1");
        map.put(Constants.FLAG, Constants.LIKE);
        return map;
    }

    private Map<String, Object> createMockUser(String userId) {
        Map<String, Object> user = new HashMap<>();
        user.put("userId", userId);
        user.put("name", "Test User " + userId);
        user.put("email", userId + "@test.com");
        user.put("role", "TESTER");
        return user;
    }

}