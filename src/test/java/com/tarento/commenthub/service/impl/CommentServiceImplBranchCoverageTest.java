package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.authentication.util.AccessTokenValidator;
import com.tarento.commenthub.authentication.util.FetchUserDetails;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentTreeIdentifierDTO;
import com.tarento.commenthub.dto.CommentsResoponseDTO;
import com.tarento.commenthub.dto.SearchCriteria;
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
import com.tarento.commenthub.utility.notificationutill.NotificationTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Companion test class for {@link CommentServiceImpl}.
 *
 * The existing CommentServiceImplTest / CommentServiceImplPaginatedCommentV3Test /
 * CommentServiceImplFetchCommentFromPrimaryV3Test / CommentServiceImplFetchUsersByCommentDataTest
 * already exercise the "happy path" and several validation-failure branches of this class in
 * detail. Comparing the source line-by-line against those tests turned up a set of branches that
 * are never executed by any existing test - mainly the failure/guard-clause paths of
 * updateExistingComment(), the redis/tree-update failure catch blocks of getPersistedComment(),
 * addFirstCommentToCreateTree() and deleteCommentById(), several likeComment() branches, the
 * tagged-user fallback branch of getComments(), and the exception/validation branches of
 * generateJwtTokenKey()/generateRedisJwtTokenKey(). This class fills exactly those gaps so the
 * class can reach full coverage.
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplBranchCoverageTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentTreeRepository commentTreeRepository;

    @Mock
    private UserCommentLikeRepository userCommentLikeRepository;

    @Mock
    private CommentTreeService commentTreeService;

    @Mock
    private ContentService contentService;

    @Mock
    private AccessTokenValidator accessTokenValidator;

    @Mock
    private FetchUserDetails fetchUser;

    @Mock
    private HelperMethodService helperMethodService;

    @Mock
    private NotificationTriggerService notificationTriggerService;

    @Mock
    private CassandraOperation cassandraOperation;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    @InjectMocks
    private CommentServiceImpl commentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commentService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(commentService, "jwtSecretKey", "dummysecret");
        ReflectionTestUtils.setField(commentService, "redisTtl", 1000L);
        ReflectionTestUtils.setField(commentService, "defaultLimit", 10);
        ReflectionTestUtils.setField(commentService, "defaultOffset", 0);
    }

    // ---------------------------------------------------------------------
    // updateExistingComment - guard clauses & catch blocks
    // ---------------------------------------------------------------------

    @Test
    void updateExistingComment_missingCommentId_throwsCommentException() {
        ObjectNode payload = buildUpdatePayload("", "tree123", "user1", "user1", "Updated text");

        CommentException ex = assertThrows(CommentException.class,
                () -> commentService.updateExistingComment(payload));

        assertTrue(ex.getMessage().contains("valid commentId"));
        verify(commentRepository, org.mockito.Mockito.never()).findById(anyString());
    }

    @Test
    void updateExistingComment_commentNotFound_throwsCommentException() {
        ObjectNode payload = buildUpdatePayload("comment123", "tree123", "user1", "user1", "Updated text");
        when(commentRepository.findById("comment123")).thenReturn(Optional.empty());

        CommentException ex = assertThrows(CommentException.class,
                () -> commentService.updateExistingComment(payload));

        assertTrue(ex.getMessage().contains("not found or has been deleted"));
    }

    @Test
    void updateExistingComment_commentInactive_throwsCommentException() {
        ObjectNode payload = buildUpdatePayload("comment123", "tree123", "user1", "user1", "Updated text");
        Comment existing = buildExistingComment("comment123", "user1", "inactive", 0);
        when(commentRepository.findById("comment123")).thenReturn(Optional.of(existing));

        CommentException ex = assertThrows(CommentException.class,
                () -> commentService.updateExistingComment(payload));

        assertTrue(ex.getMessage().contains("not found or has been deleted"));
    }

    @Test
    void updateExistingComment_unauthorizedUser_throwsCommentException() {
        ObjectNode payload = buildUpdatePayload("comment123", "tree123", "different-user", "user1", "Updated text");
        Comment existing = buildExistingComment("comment123", "user1", "active", 0);
        when(commentRepository.findById("comment123")).thenReturn(Optional.of(existing));

        CommentException ex = assertThrows(CommentException.class,
                () -> commentService.updateExistingComment(payload));

        assertEquals("No access to edit the comment", ex.getMessage());
    }

    @Test
    void updateExistingComment_redisFailure_isSwallowedAndResponseStillReturned() {
        ObjectNode payload = buildUpdatePayload("comment123", "tree123", "user1", "user1", "Updated text");
        Comment existing = buildExistingComment("comment123", "user1", "active", 5);
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");

        when(commentRepository.findById("comment123")).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis unavailable"));
        when(commentTreeService.getCommentTreeById("tree123")).thenReturn(tree);

        var response = commentService.updateExistingComment(payload);

        assertNotNull(response);
        assertNotNull(response.getComment());
        assertEquals(tree, response.getCommentTree());
    }

    @Test
    void updateExistingComment_fetchCommentTreeFails_throwsRuntimeException() {
        ObjectNode payload = buildUpdatePayload("comment123", "tree123", "user1", "user1", "Updated text");
        Comment existing = buildExistingComment("comment123", "user1", "active", 5);

        when(commentRepository.findById("comment123")).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentTreeService.getCommentTreeById("tree123"))
                .thenThrow(new RuntimeException("tree lookup failed"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.updateExistingComment(payload));

        assertTrue(ex.getMessage().contains("Failed to update comment or fetch CommentTree"));
    }

    // ---------------------------------------------------------------------
    // getPersistedComment (via addFirstCommentToCreateTree) - redis catch block
    // ---------------------------------------------------------------------

    @Test
    void addFirstComment_redisStorageFails_throwsRuntimeException() {
        ObjectNode payload = buildFirstCommentPayload();
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.addFirstCommentToCreateTree(payload));

        assertTrue(ex.getMessage().contains("Failed to store comment in Redis"));
    }

    // ---------------------------------------------------------------------
    // deleteCommentById - catch block
    // ---------------------------------------------------------------------

    @Test
    void deleteCommentById_treeUpdateFails_throwsRuntimeException() {
        CommentTreeIdentifierDTO identifierDTO =
                new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");
        Comment comment = buildExistingComment("cid", "user1", "active", 0);

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user1");
        when(commentRepository.findById("cid")).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        RedisOperations redisOperations = mock(RedisOperations.class);
        when(valueOperations.getOperations()).thenReturn(redisOperations);
        doThrow(new RuntimeException("tree update failed"))
                .when(commentTreeService)
                .updateCommentTreeForDeletedComment(eq("cid"), eq(identifierDTO), eq("parent1"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.deleteCommentById("cid", identifierDTO, "token", "parent1"));

        assertTrue(ex.getMessage().contains("Failed to delete comment or update CommentTree"));
    }

    // ---------------------------------------------------------------------
    // likeComment - branches not hit by the existing test class
    // ---------------------------------------------------------------------

    @Test
    void likeComment_commentNotFound_throwsNoSuchElementException() {
        Map<String, Object> payload = validLikePayload();
        when(commentRepository.findById("c1")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () -> commentService.likeComment(payload));
    }

    @Test
    void likeComment_addToExistingRecord_notAlreadyLiked_incrementsExistingCount() {
        Map<String, Object> payload = validLikePayload();
        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put(Constants.LIKE, 3);
        Comment comment = new Comment();
        comment.setCommentData(commentData);

        UserCourseCommentLike existing = new UserCourseCommentLike();
        existing.setCommentIds(new ArrayList<>(List.of("other-comment")));

        when(commentRepository.findById("c1")).thenReturn(Optional.of(comment));
        when(userCommentLikeRepository.findById(any())).thenReturn(Optional.of(existing));
        when(commentRepository.save(any())).thenReturn(comment);

        ApiResponse response = commentService.likeComment(payload);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(4, commentData.get(Constants.LIKE).asLong());
        assertTrue(existing.getCommentIds().contains("c1"));
    }

    @Test
    void likeComment_removeExistingLike_flagKeyAbsent_setsLikeToOne() {
        Map<String, Object> payload = validLikePayload();
        ObjectNode commentData = objectMapper.createObjectNode();
        Comment comment = new Comment();
        comment.setCommentData(commentData);

        UserCourseCommentLike existing = new UserCourseCommentLike();
        existing.setCommentIds(new ArrayList<>(List.of("c1")));

        when(commentRepository.findById("c1")).thenReturn(Optional.of(comment));
        when(userCommentLikeRepository.findById(any())).thenReturn(Optional.of(existing));
        when(commentRepository.save(any())).thenReturn(comment);

        ApiResponse response = commentService.likeComment(payload);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(1, commentData.get(Constants.LIKE).asLong());
        assertFalse(existing.getCommentIds().contains("c1"));
    }

    @Test
    void likeComment_newRecord_flagKeyPresentWithCount_incrementsExisting() {
        Map<String, Object> payload = validLikePayload();
        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put(Constants.LIKE, 5);
        Comment comment = new Comment();
        comment.setCommentData(commentData);

        when(commentRepository.findById("c1")).thenReturn(Optional.of(comment));
        when(userCommentLikeRepository.findById(any())).thenReturn(Optional.empty());
        when(commentRepository.save(any())).thenReturn(comment);

        ApiResponse response = commentService.likeComment(payload);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals(6, commentData.get(Constants.LIKE).asLong());
    }

    // ---------------------------------------------------------------------
    // getComments - tagged-user fallback-to-primary branch
    // ---------------------------------------------------------------------

    @Test
    void getComments_taggedUserFetchFallsBackToPrimary() {
        CommentTreeIdentifierDTO identifierDTO =
                new CommentTreeIdentifierDTO("TEST_ENTITY", "entity123", "TEST_WORKFLOW");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");
        ObjectNode treeData = objectMapper.createObjectNode();
        ArrayNode childNodes = objectMapper.createArrayNode();
        childNodes.add("comment123");
        treeData.set(Constants.CHILD_NODES, childNodes);
        tree.setCommentTreeData(treeData);

        Comment comment = new Comment();
        comment.setCommentId("comment123");
        ObjectNode commentData = objectMapper.createObjectNode();
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "owner1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        ArrayNode taggedUsers = objectMapper.createArrayNode();
        taggedUsers.add("tagged1");
        commentData.set(Constants.TAGGED_USERS, taggedUsers);
        comment.setCommentData(commentData);

        when(commentTreeService.getCommentTree(identifierDTO)).thenReturn(tree);
        when(commentRepository.findByCommentIdInAndStatus(anyList(), eq("active")))
                .thenReturn(List.of(comment));
        when(fetchUser.fetchDataForKeys(anyList())).thenReturn(new ArrayList<>());
        Map<String, Object> primaryUser = new HashMap<>();
        primaryUser.put("userId", "x");
        when(fetchUser.fetchUserFromprimary(anyList())).thenReturn(List.of(primaryUser));

        CommentsResoponseDTO response = commentService.getComments(identifierDTO);

        assertNotNull(response);
        verify(fetchUser, org.mockito.Mockito.atLeastOnce()).fetchUserFromprimary(anyList());
    }

    // ---------------------------------------------------------------------
    // generateJwtTokenKey / generateRedisJwtTokenKey
    // ---------------------------------------------------------------------

    @Test
    void generateJwtTokenKey_missingField_throwsCommentException() {
        CommentServiceImpl service = new CommentServiceImpl();
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("TEST_ENTITY", "", "workflow1");

        CommentException ex = assertThrows(CommentException.class, () -> service.generateJwtTokenKey(dto));

        assertTrue(ex.getMessage().contains("mandatory"));
    }

    @Test
    void generateRedisJwtTokenKey_signingFails_returnsEmptyString() {
        CommentServiceImpl service = new CommentServiceImpl();
        // jwtSecretKey is left unset (null) on this fresh instance, so Algorithm.HMAC256(null)
        // throws and the catch block inside generateRedisJwtTokenKey must be exercised.
        String result = service.generateRedisJwtTokenKey("tree1", 0, 10);

        assertEquals("", result);
    }

    // ---------------------------------------------------------------------
    // updateExistingComment - the "no prior like value to copy over" branch
    // ---------------------------------------------------------------------

    @Test
    void updateExistingComment_noExistingLikeField_success() {
        ObjectNode payload = buildUpdatePayload("comment123", "tree123", "user1", "user1", "Updated text");
        // likeCount = 0 -> buildExistingComment does NOT put a "like" field, so the
        // `has(LIKE) && !get(LIKE).isNull()` guard in updateExistingComment must take its false branch.
        Comment existing = buildExistingComment("comment123", "user1", "active", 0);
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");

        when(commentRepository.findById("comment123")).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(commentTreeService.getCommentTreeById("tree123")).thenReturn(tree);

        var response = commentService.updateExistingComment(payload);

        assertNotNull(response);
        assertNotNull(response.getComment());
        assertFalse(response.getComment().getCommentData().has(Constants.LIKE));
    }

    // ---------------------------------------------------------------------
    // reportComment - "Others" reason with otherReason provided (success path)
    // ---------------------------------------------------------------------

    @Test
    void reportComment_success_withOthersReasonAndOtherReasonProvided() {
        Map<String, Object> request = new HashMap<>();
        request.put(Constants.COMMENT_ID, "cid");
        request.put(Constants.REPORTED_REASON, List.of("Others"));
        request.put(Constants.OTHER_REASON, "Some other reason");

        Comment comment = new Comment();
        comment.setCommentId("cid");
        comment.setStatus("ACTIVE");
        comment.setCommentData(objectMapper.createObjectNode());

        when(accessTokenValidator.verifyUserToken("token")).thenReturn("user-1");
        when(commentRepository.findById("cid")).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> i.getArguments()[0]);

        ApiResponse response = commentService.reportComment(request, "token");

        assertEquals(HttpStatus.OK, response.getResponseCode());
        assertEquals("Some other reason", comment.getCommentData().get(Constants.OTHER_REASON).asText());
    }

    // ---------------------------------------------------------------------
    // paginatedComment (v1) - branches not reachable from the V3-focused tests,
    // since paginatedComment() and paginatedCommentV3() are separate method bodies
    // that each need their own coverage.
    // ---------------------------------------------------------------------

    @Test
    void paginatedComment_v1_invalidPayload_returnsBadRequest() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("");
        criteria.setEntityType("");
        criteria.setEntityId("");
        criteria.setWorkflow("");

        ApiResponse response = commentService.paginatedComment(criteria, "v1");

        assertEquals(HttpStatus.BAD_REQUEST, response.getResponseCode());
        assertTrue(response.getParams().getErr().contains(Constants.COMMENT_TREE_ID));
    }

    @Test
    void paginatedComment_v1_emptyCommentTreeId_generatesTreeId_thenNotFound() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("");
        criteria.setEntityType("TEST_ENTITY");
        criteria.setEntityId("entity123");
        criteria.setWorkflow("workflow1");

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.empty());

        ApiResponse response = commentService.paginatedComment(criteria, "v1");

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
        verify(commentTreeRepository).findById(anyString());
    }

    // ---------------------------------------------------------------------
    // fetchCommentFromPrimary (v1, private - backs paginatedComment) - this block is a
    // near-duplicate of fetchCommentFromPrimaryV3 but is separate bytecode, so it needs
    // its own direct coverage the same way CommentServiceImplFetchCommentFromPrimaryV3Test
    // covers the V3 version.
    // ---------------------------------------------------------------------

    @Test
    void fetchCommentFromPrimary_taggedUserFallsBackToPrimary_andSkipsCourseDetailsWhenNoEntityId()
            throws Exception {
        Method method = CommentServiceImpl.class.getDeclaredMethod("fetchCommentFromPrimary",
                int.class, int.class, List.class, CommentTree.class, boolean.class, String.class);
        method.setAccessible(true);

        Comment comment = new Comment();
        ObjectNode commentData = objectMapper.createObjectNode();
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "owner1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        ArrayNode taggedUsers = objectMapper.createArrayNode();
        taggedUsers.add("tagged1");
        commentData.set(Constants.TAGGED_USERS, taggedUsers);
        comment.setCommentData(commentData);

        when(commentRepository.findByCommentIdIn(anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        Map<String, Object> ownerUser = new HashMap<>();
        ownerUser.put("userId", "owner1");
        when(fetchUser.fetchDataForKeys(anyList()))
                .thenReturn(List.of(ownerUser))
                .thenReturn(new ArrayList<>());
        Map<String, Object> primaryTagged = new HashMap<>();
        primaryTagged.put("userId", "tagged1");
        when(fetchUser.fetchUserFromprimary(anyList())).thenReturn(List.of(primaryTagged));

        // No ENTITY_ID on the tree data at all, so the course-details fetch must be skipped.
        CommentTree tree = new CommentTree();
        tree.setCommentTreeData(objectMapper.createObjectNode());

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(
                commentService, 0, 10, List.of("c1"), tree, false, "v1");

        assertNotNull(result);
        verify(fetchUser).fetchUserFromprimary(anyList());
        verify(contentService, org.mockito.Mockito.never())
                .readContentFromCache(anyString(), any());
    }

    // ---------------------------------------------------------------------
    // listOfComments - owner/tagged-user extraction and fallback-to-primary branches.
    // This method has its own copy of the owner/tagged-user extraction block (same shape as
    // getComments/fetchCommentFromPrimary/fetchCommentFromPrimaryV3, but separate bytecode).
    // The existing testListOfComments_withValidInput_shouldReturnSuccessResponse test builds its
    // comment data under a "source" key instead of Constants.COMMENT_SOURCE ("commentSource"), so
    // commentData.has(Constants.COMMENT_SOURCE) is actually always false there - the owner
    // extraction "true" branch, and both fallback-to-primary calls, never really execute despite
    // that test's stubbing appearing to set them up. These tests exercise them for real.
    // ---------------------------------------------------------------------

    @Test
    void listOfComments_ownerAndTaggedUserFallBackToPrimary() {
        Comment comment = new Comment();
        ObjectNode commentData = objectMapper.createObjectNode();
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, "owner1");
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        ArrayNode taggedUsers = objectMapper.createArrayNode();
        taggedUsers.add("tagged1");
        commentData.set(Constants.TAGGED_USERS, taggedUsers);
        comment.setCommentData(commentData);

        List<String> commentIds = List.of("comment1");
        when(commentRepository.findByCommentIdInAndStatusIn(eq(commentIds), anyList(), any(Sort.class)))
                .thenReturn(List.of(comment));
        when(fetchUser.fetchDataForKeys(anyList())).thenReturn(new ArrayList<>());
        Map<String, Object> ownerPrimary = new HashMap<>();
        ownerPrimary.put("userId", "owner1");
        Map<String, Object> taggedPrimary = new HashMap<>();
        taggedPrimary.put("userId", "tagged1");
        // NOTE: despite its name, the method's "commentedUserListWithoutPrefix" local variable is
        // actually built from the USER_PREFIX-ed owner id set, so the owner fallback call is made
        // with "user:owner1", not "owner1". The tagged-user fallback, by contrast, genuinely does
        // use the without-prefix list ("tagged1").
        when(fetchUser.fetchUserFromprimary(List.of(Constants.USER_PREFIX + "owner1")))
                .thenReturn(List.of(ownerPrimary));
        when(fetchUser.fetchUserFromprimary(List.of("tagged1"))).thenReturn(List.of(taggedPrimary));

        ApiResponse response = commentService.listOfComments(commentIds);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(fetchUser).fetchUserFromprimary(List.of(Constants.USER_PREFIX + "owner1"));
        verify(fetchUser).fetchUserFromprimary(List.of("tagged1"));
    }

    @Test
    void listOfComments_emptyOwnerUserId_skipsOwnerExtraction() {
        Comment comment = new Comment();
        ObjectNode commentData = objectMapper.createObjectNode();
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put(Constants.USER_ID, ""); // blank -> must be skipped
        commentData.set(Constants.COMMENT_SOURCE, commentSource);
        comment.setCommentData(commentData);

        List<String> commentIds = List.of("comment1");
        when(commentRepository.findByCommentIdInAndStatusIn(eq(commentIds), anyList(), any(Sort.class)))
                .thenReturn(List.of(comment));

        ApiResponse response = commentService.listOfComments(commentIds);

        assertEquals(HttpStatus.OK, response.getResponseCode());
        verify(fetchUser, never()).fetchDataForKeys(anyList());
        verify(fetchUser, never()).fetchUserFromprimary(anyList());
    }

    // ---------------------------------------------------------------------
    // paginatedComment (v1) - the redis-read and redis-write try/catch blocks each need their
    // own JsonProcessingException coverage. Every existing test for this method uses a real
    // ObjectMapper, which never actually throws for the plain Map/POJO data used in tests, so
    // none of these three separate catch blocks were ever exercised. Swapping in a mocked
    // ObjectMapper for just these tests lets us force each one.
    // ---------------------------------------------------------------------

    @Test
    void paginatedComment_v1_redisReadThrowsJsonProcessingException_wrapsInRuntimeException()
            throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(commentService, "objectMapper", mockMapper);

        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("tree-id");
        criteria.setOverrideCache(false);

        CommentTree tree = new CommentTree();
        tree.setCommentTreeData(objectMapper.createObjectNode());

        when(commentTreeRepository.findById("tree-id")).thenReturn(Optional.of(tree));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn("{\"bad\":\"json\"}");
        when(mockMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("bad json") {});

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.paginatedComment(criteria, "v1"));

        assertTrue(ex.getMessage().contains("Failed to deserialize JSON"));
    }

    // These two tests originally used a fully mocked ObjectMapper and stubbed convertValue(...)
    // for both List.class and Map.class - that turned out to be unreliable in practice (the
    // generic/overloaded convertValue signature doesn't always match Mockito matchers the way
    // one would expect), so both were rewritten to spy on a REAL ObjectMapper: every method
    // except writeValueAsString runs the actual Jackson implementation, and only
    // writeValueAsString is forced to throw.
    @Test
    void paginatedComment_v1_overrideCache_serializationFails_throwsRuntimeException()
            throws JsonProcessingException {
        ObjectMapper spyMapper = spy(new ObjectMapper());
        ReflectionTestUtils.setField(commentService, "objectMapper", spyMapper);

        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("tree-id");
        criteria.setOverrideCache(true);

        CommentTree tree = new CommentTree();
        ObjectNode treeData = spyMapper.createObjectNode();
        treeData.putArray(Constants.FIRST_LEVEL_NODES);
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById("tree-id")).thenReturn(Optional.of(tree));
        when(commentRepository.findByCommentIdIn(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>()));
        doThrow(new JsonProcessingException("boom") {}).when(spyMapper).writeValueAsString(any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.paginatedComment(criteria, "v1"));

        assertTrue(ex.getMessage().contains("Failed to serialize resultMap"));
    }

    @Test
    void paginatedComment_v1_cacheMissSerializationFails_throwsRuntimeException()
            throws JsonProcessingException {
        ObjectMapper spyMapper = spy(new ObjectMapper());
        ReflectionTestUtils.setField(commentService, "objectMapper", spyMapper);

        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("tree-id");
        criteria.setOverrideCache(false);

        CommentTree tree = new CommentTree();
        ObjectNode treeData = spyMapper.createObjectNode();
        treeData.putArray(Constants.FIRST_LEVEL_NODES);
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById("tree-id")).thenReturn(Optional.of(tree));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // Cache miss: nothing stored in redis yet, so resultMap stays empty and the
        // MapUtils.isEmpty(resultMap) fallback-to-primary block below is entered.
        when(valueOperations.get(anyString())).thenReturn(null);
        when(commentRepository.findByCommentIdIn(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>()));
        doThrow(new JsonProcessingException("boom") {}).when(spyMapper).writeValueAsString(any());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentService.paginatedComment(criteria, "v1"));

        assertTrue(ex.getMessage().contains("Failed to serialize resultMap"));
    }

    // ---------------------------------------------------------------------
    // paginatedCommentV3 - the initial redis lookup is wrapped in a broad catch(Exception) that
    // simply logs and falls through to the postgres lookup. None of the existing V3 tests force
    // this specific path (redis itself failing, as opposed to a cache miss).
    // ---------------------------------------------------------------------

    @Test
    void paginatedCommentV3_redisLookupThrows_fallsBackToPostgresNotFound() {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("tree-id");
        criteria.setOverrideCache(false);

        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));
        when(commentTreeRepository.findById("tree-id")).thenReturn(Optional.empty());

        ApiResponse response = commentService.paginatedCommentV3(criteria);

        assertEquals(HttpStatus.NOT_FOUND, response.getResponseCode());
    }

    @Test
    void paginatedCommentV3_postgresCacheWriteFails_isSwallowedWithoutPropagating()
            throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        ReflectionTestUtils.setField(commentService, "objectMapper", mockMapper);

        SearchCriteria criteria = new SearchCriteria();
        criteria.setCommentTreeId("tree-id");
        criteria.setOverrideCache(false);

        CommentTree tree = new CommentTree();
        ObjectNode treeData = objectMapper.createObjectNode();
        treeData.putArray(Constants.FIRST_LEVEL_NODES);
        tree.setCommentTreeData(treeData);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // First lookup (cached comment tree) misses, so the code falls through to postgres.
        when(valueOperations.get(anyString())).thenReturn(null);
        when(commentTreeRepository.findById("tree-id")).thenReturn(Optional.of(tree));
        Map<String, Object> convertedTreeData = new HashMap<>();
        convertedTreeData.put(Constants.FIRST_LEVEL_NODES, new ArrayList<>());
        when(mockMapper.convertValue(eq(treeData), eq(Map.class))).thenReturn(convertedTreeData);
        // childNodeList must never be null - fetchCommentFromPrimaryV3 unconditionally calls
        // .size() on it via Optional.ofNullable(comments).ifPresent(...).
        when(mockMapper.convertValue(any(), eq(List.class))).thenReturn(new ArrayList<>());
        // Serializing the postgres result for the redis cache write throws - this must be
        // swallowed silently (only logged) rather than aborting the whole request, since the
        // catch here is JsonProcessingException-specific and never rethrows.
        when(mockMapper.writeValueAsString(convertedTreeData))
                .thenThrow(new JsonProcessingException("boom") {});
        when(commentRepository.findByCommentIdIn(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(new ArrayList<>()));

        ApiResponse response = assertDoesNotThrow(() -> commentService.paginatedCommentV3(criteria));

        // commentResultMap was still populated from postgres before the cache-write failed, so
        // the request completes normally (response code is never touched again after the
        // initial "not found" check, which this already passed).
        assertEquals(HttpStatus.OK, response.getResponseCode());
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private ObjectNode buildUpdatePayload(String commentId, String commentTreeId, String sourceUserId,
            String existingRecordUserId, String commentText) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put(Constants.COMMENT_ID, commentId);
        payload.put(Constants.COMMENT_TREE_ID, commentTreeId);
        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put("comment", commentText);
        commentData.put("commentResolved", "false");
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put("userId", sourceUserId);
        commentSource.put("userPic", "https://example.com/pic.jpg");
        commentSource.put("userRole", "TESTER");
        commentData.set("commentSource", commentSource);
        payload.set(Constants.COMMENT_DATA, commentData);
        return payload;
    }

    private Comment buildExistingComment(String commentId, String userId, String status, int likeCount) {
        Comment comment = new Comment();
        comment.setCommentId(commentId);
        comment.setStatus(status);
        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put("comment", "Original comment");
        commentData.put("commentResolved", "false");
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put("userId", userId);
        commentData.set("commentSource", commentSource);
        if (likeCount > 0) {
            commentData.put("like", likeCount);
        }
        comment.setCommentData(commentData);
        return comment;
    }

    private ObjectNode buildFirstCommentPayload() {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode commentData = objectMapper.createObjectNode();
        commentData.put("comment", "First test comment");
        ObjectNode commentSource = objectMapper.createObjectNode();
        commentSource.put("userId", "user1");
        commentSource.put("userPic", "https://example.com/pic.jpg");
        commentSource.put("userRole", "TESTER");
        commentData.set("commentSource", commentSource);
        payload.set("commentData", commentData);
        ObjectNode commentTreeData = objectMapper.createObjectNode();
        commentTreeData.put("entityId", "entity123");
        commentTreeData.put("entityType", "TEST_ENTITY");
        commentTreeData.put("workflow", "DEFAULT_WORKFLOW");
        payload.set("commentTreeData", commentTreeData);
        return payload;
    }

    private Map<String, Object> validLikePayload() {
        Map<String, Object> map = new HashMap<>();
        map.put(Constants.COMMENT_ID, "c1");
        map.put(Constants.USERID, "u1");
        map.put(Constants.COURSEID, "course1");
        map.put(Constants.FLAG, Constants.LIKE);
        return map;
    }
}
