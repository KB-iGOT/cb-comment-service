package com.tarento.commenthub.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Companion test class for {@link CommentTreeServiceImpl}.
 *
 * CommentTreeServiceImplTest / CommentTreeServiceImplMethodTest already exercise the
 * "happy path" and most guard-clause branches of this class. Comparing the source
 * line-by-line against those tests turned up branches that no existing test reaches:
 * the redis-serialization catch(JsonProcessingException) blocks that createCommentTree(),
 * updateCommentTree() and updateCommentTreeForDeletedComment() each have their own copy of,
 * the "hierarchy path resolves to a plain comment object (not an array)" branch inside
 * updateCommentTree(), a couple of findTargetNode() branches, and a few of the
 * updateCommentTreeForDeletedComment() comment-removal sub-branches (children array that's
 * still non-empty after removal, a matched parent with no children field at all, and the
 * literal-string "null" parentId case). This class fills exactly those gaps.
 */
@ExtendWith(MockitoExtension.class)
class CommentTreeServiceImplBranchCoverageTest {

    @InjectMocks
    private CommentTreeServiceImpl commentTreeService;

    @Mock
    private CommentTreeRepository commentTreeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RedisTemplate redisTemplate;

    @Mock
    private ValueOperations valueOperations;

    private static final String COMMENT_ID = "c1";
    private static final String PARENT_ID = "parent1";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commentTreeService, "jwtSecretKey", "testSecret");
        ReflectionTestUtils.setField(commentTreeService, "redisTtl", 300L);
        ReflectionTestUtils.setField(commentTreeService, "redisTemplate", redisTemplate);
    }

    // createCommentTree - redis-serialization catch(JsonProcessingException) block.
    // No existing test forces objectMapper.writeValueAsString(...) to fail for this method,
    // so that catch block (and the "Failed to serialize resultMap" wrapping) never runs.

    @Test
    void createCommentTree_redisSerializationFails_wrapsInCommentException() throws Exception {
        JsonNode payload = createDummyPayload();

        when(commentTreeRepository.getIdCount(anyString())).thenReturn(0);
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(commentTreeRepository.save(any(CommentTree.class))).thenAnswer(i -> i.getArguments()[0]);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        CommentException ex = assertThrows(CommentException.class,
                () -> commentTreeService.createCommentTree(payload));

        assertTrue(ex.getMessage().contains("Failed to serialize resultMap"));
    }

    // updateCommentTree - same redis-serialization catch block, but this method's own
    // copy of the try/catch (separate bytecode from createCommentTree's).

    @Test
    void updateCommentTree_redisSerializationFails_wrapsInCommentException() throws Exception {
        JsonNode payload = createRootLevelUpdatePayload();
        ObjectNode treeData = createExistingCommentTreeData();
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(tree));
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        when(commentTreeRepository.save(any())).thenReturn(tree);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        CommentException ex = assertThrows(CommentException.class,
                () -> commentTreeService.updateCommentTree(payload));

        assertTrue(ex.getMessage().contains("Failed to serialize resultMap"));
    }

    // ---------------------------------------------------------------------
    // updateCommentTree - hierarchy path resolves to a plain comment object that has no
    // CHILDREN field yet (targetJsonNode.isArray() == false, and its CHILDREN is null),
    // so a brand new children array has to be created via putArray(). No existing test's
    // hierarchy path lands on anything but an (existing) children array.
    // ---------------------------------------------------------------------

    @Test
    void updateCommentTree_hierarchyPathResolvesToObjectWithoutChildren_createsChildrenArray() throws Exception {
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode payload = realMapper.createObjectNode();
        payload.put(Constants.COMMENT_TREE_ID, "tree123");
        payload.put(Constants.COMMENT_ID, "newComment");
        payload.putArray(Constants.HIERARCHY_PATH).add("parent1");

        ObjectNode parentComment = realMapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, "parent1"); // intentionally no CHILDREN field
        ArrayNode comments = realMapper.createArrayNode().add(parentComment);

        ObjectNode treeData = realMapper.createObjectNode();
        treeData.set(Constants.COMMENTS, comments);
        treeData.putArray(Constants.CHILD_NODES);
        treeData.putArray(Constants.FIRST_LEVEL_NODES);

        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(tree));
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        when(objectMapper.treeToValue(any(), eq(String[].class))).thenReturn(new String[]{"parent1"});
        when(commentTreeRepository.save(any())).thenReturn(tree);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CommentTree result = commentTreeService.updateCommentTree(payload);

        assertNotNull(result);
        JsonNode updatedParent = treeData.get(Constants.COMMENTS).get(0);
        assertTrue(updatedParent.has(Constants.CHILDREN));
        assertEquals(1, updatedParent.get(Constants.CHILDREN).size());
    }

    // ---------------------------------------------------------------------
    // findTargetNode - branches not reached by the single recursive-match test elsewhere:
    // a hierarchy segment that matches nothing, and a currentNode that isn't an array at all.
    // ---------------------------------------------------------------------

    @Test
    void findTargetNode_noSegmentMatches_returnsNull() throws Exception {
        JsonNode node = new ObjectMapper().readTree("[{\"commentId\": \"1\"}]");

        JsonNode result = CommentTreeServiceImpl.findTargetNode(node, new String[]{"nonexistent"}, 0);

        assertNull(result);
    }

    @Test
    void findTargetNode_currentNodeNotArray_returnsNull() {
        JsonNode node = new ObjectMapper().createObjectNode().put("foo", "bar");

        JsonNode result = CommentTreeServiceImpl.findTargetNode(node, new String[]{"1"}, 0);

        assertNull(result);
    }

    // ---------------------------------------------------------------------
    // updateCommentTreeForDeletedComment - redis-serialization catch block (its own copy,
    // separate from create/update's).
    // ---------------------------------------------------------------------

    @Test
    void updateCommentTreeForDeletedComment_redisSerializationFails_throwsRuntimeException() throws Exception {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("treeId");

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode treeData = realMapper.createObjectNode();
        treeData.set(Constants.CHILD_NODES, realMapper.createArrayNode().add(COMMENT_ID));
        treeData.set(Constants.FIRST_LEVEL_NODES, realMapper.createArrayNode());
        treeData.set(Constants.COMMENTS, realMapper.createArrayNode()); // empty, loop is a no-op
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {
        });

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> commentTreeService.updateCommentTreeForDeletedComment(COMMENT_ID, dto, PARENT_ID));

        assertTrue(ex.getMessage().contains("Failed to serialize resultMap"));
    }

    // ---------------------------------------------------------------------
    // updateCommentTreeForDeletedComment - the matched parent's children array still has
    // entries left after removal, so the "remove the now-empty CHILDREN key" line must NOT run.
    // ---------------------------------------------------------------------

    @Test
    void updateCommentTreeForDeletedComment_multipleChildren_keepsChildrenArrayWhenNotEmpty() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("treeId");

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode parentComment = realMapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, "parent1");
        ObjectNode child1 = realMapper.createObjectNode();
        child1.put(Constants.COMMENT_ID, "c1");
        ObjectNode child2 = realMapper.createObjectNode();
        child2.put(Constants.COMMENT_ID, "c2");
        ArrayNode children = realMapper.createArrayNode().add(child1).add(child2);
        parentComment.set(Constants.CHILDREN, children);

        ArrayNode comments = realMapper.createArrayNode().add(parentComment);
        ObjectNode treeData = realMapper.createObjectNode();
        treeData.set(Constants.COMMENTS, comments);
        treeData.set(Constants.CHILD_NODES, realMapper.createArrayNode().add("c1"));
        treeData.set(Constants.FIRST_LEVEL_NODES, realMapper.createArrayNode());
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        commentTreeService.updateCommentTreeForDeletedComment("c1", dto, "parent1");

        assertTrue(parentComment.has(Constants.CHILDREN));
        assertEquals(1, parentComment.get(Constants.CHILDREN).size());
        verify(commentTreeRepository).save(tree);
    }

    // ---------------------------------------------------------------------
    // updateCommentTreeForDeletedComment - the matched parent has no CHILDREN field at all,
    // so the inner removal loop must be skipped entirely (and the method must still just
    // break out cleanly, with no exception).
    // ---------------------------------------------------------------------

    @Test
    void updateCommentTreeForDeletedComment_matchedParentHasNoChildrenField_breaksWithoutError() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("treeId");

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode parentComment = realMapper.createObjectNode();
        parentComment.put(Constants.COMMENT_ID, "parent1"); // no CHILDREN field

        ArrayNode comments = realMapper.createArrayNode().add(parentComment);
        ObjectNode treeData = realMapper.createObjectNode();
        treeData.set(Constants.COMMENTS, comments);
        treeData.set(Constants.CHILD_NODES, realMapper.createArrayNode().add("c1"));
        treeData.set(Constants.FIRST_LEVEL_NODES, realMapper.createArrayNode());
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        commentTreeService.updateCommentTreeForDeletedComment("c1", dto, "parent1");

        assertFalse(parentComment.has(Constants.CHILDREN));
        verify(commentTreeRepository).save(tree);
    }

    // ---------------------------------------------------------------------
    // updateCommentTreeForDeletedComment - the literal string "null" as parentId. This is a
    // distinct short-circuit branch from an actual null parentId (which every other
    // top-level-removal test uses), since it's the middle clause of the OR that gets hit.
    // ---------------------------------------------------------------------

    @Test
    void updateCommentTreeForDeletedComment_parentIdLiteralNullString_removesTopLevelComment() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("treeId");

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode commentNode = realMapper.createObjectNode();
        commentNode.put(Constants.COMMENT_ID, "c1");
        ArrayNode comments = realMapper.createArrayNode().add(commentNode);

        ObjectNode treeData = realMapper.createObjectNode();
        treeData.set(Constants.COMMENTS, comments);
        treeData.set(Constants.CHILD_NODES, realMapper.createArrayNode().add("c1"));
        treeData.set(Constants.FIRST_LEVEL_NODES, realMapper.createArrayNode().add("c1"));
        tree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        commentTreeService.updateCommentTreeForDeletedComment("c1", dto, "null");

        assertEquals(0, treeData.get(Constants.COMMENTS).size());
        verify(commentTreeRepository).save(tree);
    }

    // ---------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------

    private JsonNode createDummyPayload() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();

        ObjectNode commentTreeData = mapper.createObjectNode();
        commentTreeData.put("entityType", "type1");
        commentTreeData.put("entityId", "id1");
        commentTreeData.put("workflow", "wf1");

        rootNode.set(Constants.COMMENT_TREE_DATA, commentTreeData);
        rootNode.put(Constants.COMMENT_ID, "comment123");

        return rootNode;
    }

    private JsonNode createRootLevelUpdatePayload() {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put(Constants.COMMENT_TREE_ID, "tree123");
        payload.put(Constants.COMMENT_ID, "comment456");
        payload.putArray(Constants.HIERARCHY_PATH); // empty -> root-level branch
        return payload;
    }

    private ObjectNode createExistingCommentTreeData() {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.putArray(Constants.COMMENTS);
        node.putArray(Constants.CHILD_NODES);
        node.putArray(Constants.FIRST_LEVEL_NODES);
        return node;
    }
}
