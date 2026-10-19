package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tarento.commenthub.constant.Constants;
import com.tarento.commenthub.dto.CommentTreeIdentifierDTO;
import com.tarento.commenthub.entity.CommentTree;
import com.tarento.commenthub.exception.CommentException;
import com.tarento.commenthub.repository.CommentTreeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentTreeServiceImplMethodTest {

    @InjectMocks
    private CommentTreeServiceImpl commentTreeService;

    @Mock
    private CommentTreeRepository commentTreeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    @Captor
    private ArgumentCaptor<String> redisKeyCaptor;

    private static final String COMMENT_ID = "c1";
    private static final String PARENT_ID = "p1";
    private static final String COMMENT_TREE_ID = "ct1";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(commentTreeService, "redisTtl", 60L);
        ReflectionTestUtils.setField(commentTreeService, "jwtSecretKey", "test-secret");
        ReflectionTestUtils.setField(commentTreeService, "redisTemplate", redisTemplate);
    }

    private ObjectNode buildMockJsonTree(boolean includeFirstLevel, boolean includeChild, boolean nestedCommentMatch) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();

        ArrayNode childNodes = mapper.createArrayNode().add(COMMENT_ID);
        ArrayNode firstLevelNodes = mapper.createArrayNode();
        if (includeFirstLevel) firstLevelNodes.add(COMMENT_ID);

        ObjectNode parentCommentNode = mapper.createObjectNode();
        parentCommentNode.put(Constants.COMMENT_ID, nestedCommentMatch ? PARENT_ID : "other");

        ArrayNode children = mapper.createArrayNode();
        if (includeChild) {
            ObjectNode child = mapper.createObjectNode();
            child.put(Constants.COMMENT_ID, COMMENT_ID);
            children.add(child);
        }
        if (includeChild) parentCommentNode.set(Constants.CHILDREN, children);

        ArrayNode comments = mapper.createArrayNode().add(parentCommentNode);

        jsonNode.set(Constants.CHILD_NODES, childNodes);
        jsonNode.set(Constants.FIRST_LEVEL_NODES, firstLevelNodes);
        jsonNode.set(Constants.COMMENTS, comments);

        return jsonNode;
    }

    @Test
    void test_updateCommentTree_removesCommentFromAllPlaces() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);
        ObjectNode jsonNode = buildMockJsonTree(true, true, true);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID);

        verify(commentTreeRepository).save(any(CommentTree.class));
        verify(valueOps).set(redisKeyCaptor.capture(), any(), eq(60L), eq(TimeUnit.SECONDS));
        assertTrue(redisKeyCaptor.getValue().contains(Constants.COMMENT_TREE_REDIS_KEY));
    }

    @Test
    void test_commentIdNotFound_shouldThrow() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        // Create tree without the comment ID in childNodes
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add("different-id"));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, mapper.createArrayNode());

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));

        CommentException ex = assertThrows(CommentException.class, () ->
                commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, "otherParent"));

        assertTrue(ex.getMessage().contains("not found in the specified comment tree"));
    }

    @Test
    void test_topLevelCommentRemovedWhenParentIdNull() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();

        ArrayNode childNodes = mapper.createArrayNode().add(COMMENT_ID);
        ArrayNode firstLevelNodes = mapper.createArrayNode().add(COMMENT_ID);

        ObjectNode commentNode = mapper.createObjectNode();
        commentNode.put(Constants.COMMENT_ID, COMMENT_ID);

        ArrayNode comments = mapper.createArrayNode().add(commentNode);

        jsonNode.set(Constants.CHILD_NODES, childNodes);
        jsonNode.set(Constants.FIRST_LEVEL_NODES, firstLevelNodes);
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, null);

        verify(commentTreeRepository).save(tree);
        verify(valueOps).set(any(), any(), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void test_noCommentsNodePresent_returnsEarly() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        // no comments node - this will cause early return

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID);

        // Method returns early when comments node is null, so no save or redis operations
        verify(commentTreeRepository, never()).save(any());
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void test_commentTreeNotFound_doesNothing() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.empty());

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, null);

        verify(commentTreeRepository, never()).save(any());
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    @Test
    void test_loopExitsEarlyWhenMatchFoundBeforeLastElement() {
        // Two top-level comments, with the parent match occurring at index 0.
        // Exercises the for-loop condition's "matchIndex < 0 becomes false while
        // i < comments.size() is still true" early-exit branch.
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();

        ObjectNode parentComment = mapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, PARENT_ID);
        ObjectNode childEntry = mapper.createObjectNode();
        childEntry.put(Constants.COMMENT_ID, COMMENT_ID);
        ArrayNode children = mapper.createArrayNode().add(childEntry);
        parentComment.set(Constants.CHILDREN, children);

        ObjectNode secondComment = mapper.createObjectNode();
        secondComment.put(Constants.COMMENT_ID, "secondTopLevel");

        ArrayNode comments = mapper.createArrayNode().add(parentComment).add(secondComment);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID);

        // The only child was removed, so the now-empty CHILDREN array is pruned
        // from the parent entirely (see removeChildComment's isEmpty() cleanup).
        assertFalse(parentComment.has(Constants.CHILDREN));
        assertEquals(2, comments.size());
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_emptyStringParentId_treatedAsTopLevelDeletion() {
        // parentId = "" exercises the parentId.isEmpty() disjunct on line 302,
        // and the !parentId.isEmpty() false short-circuit on line 299.
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();
        commentNode.put(Constants.COMMENT_ID, COMMENT_ID);
        ArrayNode comments = mapper.createArrayNode().add(commentNode);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, "");

        assertEquals(0, comments.size());
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_literalNullStringParentId_treatedAsTopLevelDeletion() {
        // parentId = "null" (the literal string) exercises the
        // "null".equalsIgnoreCase(parentId) disjunct on line 302, combined with a
        // non-null/non-empty parentId that doesn't match any comment on line 299.
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();
        commentNode.put(Constants.COMMENT_ID, COMMENT_ID);
        ArrayNode comments = mapper.createArrayNode().add(commentNode);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, "null");

        assertEquals(0, comments.size());
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_nonMatchingParentId_noRemovalButStillSaves() {
        // parentId is non-null/non-empty but matches no comment, so line 299 is
        // false and the left-hand disjunction on line 302 is also false
        // (short-circuiting before the commentId check). Nothing should be
        // removed, yet removeFromComments still returns true so save proceeds.
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();
        commentNode.put(Constants.COMMENT_ID, "unrelatedComment");
        ArrayNode comments = mapper.createArrayNode().add(commentNode);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, "nonMatchingParent");

        assertEquals(1, comments.size());
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_nullParentIdWithMismatchedCommentId_noRemoval() {
        // parentId == null makes the left-hand disjunction on line 302 true, but
        // commentId doesn't match, making commentId.equalsIgnoreCase(...) false -
        // covering the "left true, right false" combination on line 302/303.
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode commentNode = mapper.createObjectNode();
        commentNode.put(Constants.COMMENT_ID, "unrelatedComment");
        ArrayNode comments = mapper.createArrayNode().add(commentNode);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, null);

        assertEquals(1, comments.size());
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_removeChildComment_whenParentHasNoChildrenField_returnsEarly() {
        // matchIsChildOfParent is true, but the matched parent node has no
        // CHILDREN field at all, so removeChildComment must return immediately
        // without throwing (covers the children == null branch).
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode parentComment = mapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, PARENT_ID);
        // Intentionally no CHILDREN field on parentComment.

        ArrayNode comments = mapper.createArrayNode().add(parentComment);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertDoesNotThrow(() ->
                commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID));

        assertFalse(parentComment.has(Constants.CHILDREN));
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_removeChildComment_whenTargetChildNotPresentAmongSiblings_loopCompletesWithoutMatch() {
        // The children array has entries, but none match the commentId being
        // removed, so the inner for-loop runs to completion without hitting the
        // break at line 330 (covers the natural loop-exhaustion exit).
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode parentComment = mapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, PARENT_ID);

        ObjectNode siblingA = mapper.createObjectNode();
        siblingA.put(Constants.COMMENT_ID, "siblingA");
        ObjectNode siblingB = mapper.createObjectNode();
        siblingB.put(Constants.COMMENT_ID, "siblingB");
        ArrayNode children = mapper.createArrayNode().add(siblingA).add(siblingB);
        parentComment.set(Constants.CHILDREN, children);

        ArrayNode comments = mapper.createArrayNode().add(parentComment);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID);

        assertEquals(2, parentComment.get(Constants.CHILDREN).size());
        verify(commentTreeRepository).save(any(CommentTree.class));
    }

    @Test
    void test_removeChildComment_whenSiblingsRemainAfterRemoval_keepsChildrenArray() {
        // After removing the matched child, siblings remain, so
        // children.isEmpty() is false and the CHILDREN field must NOT be
        // removed from the parent (covers the isEmpty()==false branch).
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId(COMMENT_TREE_ID);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode jsonNode = mapper.createObjectNode();
        ObjectNode parentComment = mapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, PARENT_ID);

        ObjectNode targetChild = mapper.createObjectNode();
        targetChild.put(Constants.COMMENT_ID, COMMENT_ID);
        ObjectNode siblingB = mapper.createObjectNode();
        siblingB.put(Constants.COMMENT_ID, "siblingB");
        ArrayNode children = mapper.createArrayNode().add(targetChild).add(siblingB);
        parentComment.set(Constants.CHILDREN, children);

        ArrayNode comments = mapper.createArrayNode().add(parentComment);

        jsonNode.set(Constants.CHILD_NODES, mapper.createArrayNode().add(COMMENT_ID));
        jsonNode.set(Constants.FIRST_LEVEL_NODES, mapper.createArrayNode());
        jsonNode.set(Constants.COMMENTS, comments);

        tree.setCommentTreeData(jsonNode);
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(JsonNode.class), eq(Map.class))).thenReturn(Map.of("dummy", "data"));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID);

        assertEquals(1, parentComment.get(Constants.CHILDREN).size());
        assertTrue(parentComment.has(Constants.CHILDREN));
        verify(commentTreeRepository).save(any(CommentTree.class));
    }
}

