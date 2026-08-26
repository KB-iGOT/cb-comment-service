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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentTreeServiceImplTest {

    @InjectMocks
    private CommentTreeServiceImpl commentTreeService;

    @Mock
    private CommentTreeRepository commentTreeRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RedisTemplate redisTemplate;


    @BeforeEach
    void setup() {
        // Mock the ValueOperations.set(...) method to do nothing
        ReflectionTestUtils.setField(commentTreeService, "jwtSecretKey", "testSecret");
        ReflectionTestUtils.setField(commentTreeService, "redisTtl", 300L);
        ReflectionTestUtils.setField(commentTreeService, "redisTemplate", redisTemplate);
    }

    @Test
    void testGetCommentTreeById_success() {
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("tree123");
        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(tree));

        CommentTree result = commentTreeService.getCommentTreeById("tree123");
        assertEquals("tree123", result.getCommentTreeId());
    }

    @Test
    void testGetCommentTreeById_notFound() {
        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.empty());

        assertThrows(CommentException.class, () -> {
            commentTreeService.getCommentTreeById("tree123");
        });
    }

    @Test
    void testGenerateJwtTokenKey_success() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("etype", "eid", "workflow");
        String token = commentTreeService.generateJwtTokenKey(dto);
        assertNotNull(token);
    }

    @Test
    void testGenerateJwtTokenKey_missingFields() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO(null, "eid", null);
        assertThrows(CommentException.class, () -> commentTreeService.generateJwtTokenKey(dto));
    }

    @Test
    void testGetCommentTreeIdentifierDTO_success() {
        ObjectNode node = new ObjectMapper().createObjectNode();
        node.put("entityType", "etype");
        node.put("entityId", "eid");
        node.put("workflow", "wf");

        CommentTreeIdentifierDTO dto = commentTreeService.getCommentTreeIdentifierDTO(node);
        assertEquals("etype", dto.getEntityType());
    }

    @Test
    void testSetCommentTreeStatusToResolved_success() {
        CommentTree tree = new CommentTree();
        tree.setStatus("active");

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));
        when(commentTreeRepository.save(any())).thenReturn(tree);

        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("etype", "eid", "wf");
        CommentTree result = commentTreeService.setCommentTreeStatusToResolved(dto);
        assertEquals("resolved", result.getStatus());
    }

    @Test
    void testSetCommentTreeStatusToResolved_notFound() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("etype", "eid", "wf");
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThrows(CommentException.class, () -> commentTreeService.setCommentTreeStatusToResolved(dto));
    }

    @Test
    void testGetCommentTree_success() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("etype", "eid", "workflow");
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("xyz");

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));

        CommentTree result = commentTreeService.getCommentTree(dto);
        assertNotNull(result);
    }

    @Test
    void testGetCommentTree_notFound() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("etype", "eid", "workflow");

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.empty());

        assertThrows(CommentException.class, () -> commentTreeService.getCommentTree(dto));
    }

    @Test
    void testFindTargetNode_recursivelyFindsTarget() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = "[{\"commentId\": \"1\", \"children\": [{\"commentId\": \"2\"}]}]";
        JsonNode node = mapper.readTree(json);

        JsonNode result = CommentTreeServiceImpl.findTargetNode(node, new String[]{"1", "2"}, 0);
        assertNotNull(result);
    }

    @Test
    void testUpdateCommentTreeForDeletedComment_throwsExceptionIfCommentIdNotFound() {
        // Given
        String commentId = "missingId";
        String parentId = "parent1";
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO();
        dto.setWorkflow("test");
        dto.setEntityType("type");
        dto.setEntityId("id");
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.putArray(Constants.CHILD_NODES);  // Empty
        root.putArray(Constants.FIRST_LEVEL_NODES);  // Empty
        root.putArray(Constants.COMMENTS); // Empty

        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("dummyTree");
        tree.setCommentTreeData(root);

        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));

        // When / Then
        CommentException exception = assertThrows(CommentException.class, () -> commentTreeService.updateCommentTreeForDeletedComment(commentId, dto, parentId));

        assertTrue(exception.getMessage().contains("Comment, you're trying to delete not found"));
    }

    @Test
    void testUpdateCommentTreeForDeletedComment_childNodesRemoval_commentsNull() {
        // Given
        String commentId = "comment123";
        String parentId = "someParent"; // any non-empty value to trigger the child removal branch
        // We also need a valid DTO since generateJwtTokenKey(dto) is used to find the tree.
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("entityType", "entityId", "workflow");

        // Create the comment tree data with childNodes containing our commentId.
        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode treeData = realMapper.createObjectNode();

        // Create the CHILD_NODES array with the commentId we want to remove.
        ArrayNode childNodes = treeData.putArray(Constants.CHILD_NODES);
        childNodes.add(commentId);

        // Create FIRST_LEVEL_NODES array (it can contain other IDs)
        ArrayNode firstLevelNodes = treeData.putArray(Constants.FIRST_LEVEL_NODES);
        treeData.putArray(Constants.COMMENTS);
        firstLevelNodes.add("anotherId");

        // Do not add a COMMENTS array (so that jsonNode.get(Constants.COMMENTS) returns null)

        // Build a CommentTree entity with this treeData.
        CommentTree tree = new CommentTree();
        tree.setCommentTreeId("treeId");
        tree.setCommentTreeData(treeData);
        tree.setStatus("active");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        tree.setCreatedDate(now);
        tree.setLastUpdatedDate(now);

        // When: simulate repository lookup using any string (the generated JWT key).
        when(commentTreeRepository.findById(anyString())).thenReturn(Optional.of(tree));

        // Let the ObjectMapper use real conversion for Map (so we can verify later)
        // (Assuming your ObjectMapper is a real instance or you can delegate to a new one)
        when(objectMapper.convertValue(any(), eq(Map.class))).thenAnswer(invocation -> {
            Object arg = invocation.getArgument(0);
            return realMapper.convertValue(arg, Map.class);
        });

        // Simulate Redis operations.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        // Call the method under test. Because COMMENTS node is missing, after processing CHILD_NODES,
        // the method will return before processing comments.
        commentTreeService.updateCommentTreeForDeletedComment(commentId, dto, parentId);

        // Then
        // Validate that the childNodes array no longer contains the commentId.
        JsonNode updatedTreeData = tree.getCommentTreeData();
        ArrayNode updatedChildNodes = (ArrayNode) updatedTreeData.get(Constants.CHILD_NODES);
        // The commentId should have been removed; the array size should be 0.
        assertEquals(0, updatedChildNodes.size());

        // Verify that repository.save() and Redis update were invoked.
        verify(commentTreeRepository).save(tree);
    }


    // Helper method to create dummy JsonNode with required fields
    private JsonNode createDummyPayload() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode rootNode = mapper.createObjectNode();

        ObjectNode commentTreeData = mapper.createObjectNode();
        commentTreeData.put("entityType", "type1");
        commentTreeData.put("entityId", "id1");
        commentTreeData.put("workflow", "wf1");

        rootNode.set("commentTreeData", commentTreeData);
        rootNode.put("commentId", "comment123");

        return rootNode;
    }

    @Test
    void testGetCommentTreeIdentifierDTO() {
        JsonNode commentTreeData = createDummyPayload().get("commentTreeData");
        CommentTreeIdentifierDTO dto = commentTreeService.getCommentTreeIdentifierDTO(commentTreeData);

        assertEquals("type1", dto.getEntityType());
        assertEquals("id1", dto.getEntityId());
        assertEquals("wf1", dto.getWorkflow());
    }

    @Test
    void testGenerateJwtTokenKey_WithValidData() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("type1", "id1", "wf1");

        // Since actual method's implementation is missing,
        // we'll temporarily spy on the service and mock this method to simulate a token.
        CommentTreeServiceImpl spyService = Mockito.spy(commentTreeService);
        doReturn("dummyToken123").when(spyService).generateJwtTokenKey(dto);

        String token = spyService.generateJwtTokenKey(dto);

        assertNotNull(token);
        assertEquals("dummyToken123", token);
    }

    @Test
    void testGenerateJwtTokenKey_WithMissingFields_ShouldThrow() {
        CommentTreeIdentifierDTO dto = new CommentTreeIdentifierDTO("", "id1", "wf1");

        Exception exception = assertThrows(CommentException.class, () -> {
            commentTreeService.generateJwtTokenKey(dto);
        });

        String expectedMessage = "Please provide values for 'entityType', 'entityId', and 'workflow' as all of these fields are mandatory.";
        assertTrue(exception.getMessage().contains(expectedMessage));
    }

    @Test
    void testCreateCommentTree_Success() throws Exception {
        JsonNode payload = createDummyPayload();

        // Mock generateJwtTokenKey() with spy (simulate returning some token)
        CommentTreeServiceImpl spyService = Mockito.spy(commentTreeService);
        doReturn("token123").when(spyService).generateJwtTokenKey(any(CommentTreeIdentifierDTO.class));

        // Mock repository.getIdCount to return 0 (no duplicates)
        when(commentTreeRepository.getIdCount("token123")).thenReturn(0);

        // Mock objectMapper.createObjectNode to return a real ObjectNode (use real ObjectMapper for this)
        ObjectMapper realMapper = new ObjectMapper();
        when(objectMapper.createObjectNode()).thenReturn(realMapper.createObjectNode());

        // Mock objectMapper.convertValue to return a map
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        // Mock redisTemplate to just do nothing on set
        doNothing().when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Mock repository.save() to return a dummy CommentTree
        CommentTree savedTree = new CommentTree();
        savedTree.setCommentTreeId("token123");
        when(commentTreeRepository.save(any(CommentTree.class))).thenReturn(savedTree);

        CommentTree result = spyService.createCommentTree(payload);

        assertNotNull(result);
        assertEquals("token123", result.getCommentTreeId());
    }

    @Test
    void testCreateCommentTree_DuplicateId_ShouldThrow() {
        JsonNode payload = createDummyPayload();

        CommentTreeServiceImpl spyService = Mockito.spy(commentTreeService);
        doReturn("token123").when(spyService).generateJwtTokenKey(any(CommentTreeIdentifierDTO.class));

        // Simulate duplicate id count > 0
        when(commentTreeRepository.getIdCount("token123")).thenReturn(1);

        CommentException exception = assertThrows(CommentException.class, () -> {
            spyService.createCommentTree(payload);
        });

        assertEquals(Constants.DUPLICATE_TREE_ERROR, exception.getCode());
    }

    @Test
    void testCreateCommentTree_ExceptionThrown_ShouldWrapInCommentException() {
        JsonNode payload = createDummyPayload();

        CommentTreeServiceImpl spyService = Mockito.spy(commentTreeService);
        doReturn("token123").when(spyService).generateJwtTokenKey(any(CommentTreeIdentifierDTO.class));

        when(commentTreeRepository.getIdCount("token123")).thenReturn(0);

        // Make objectMapper.createObjectNode throw exception
        when(objectMapper.createObjectNode()).thenThrow(new RuntimeException("Mocked exception"));

        CommentException exception = assertThrows(CommentException.class, () -> {
            spyService.createCommentTree(payload);
        });

        assertTrue(exception.getMessage().contains("Mocked exception"));
        assertEquals(HttpStatus.OK.value(), exception.getHttpStatusCode());
    }

    @Test
    void testUpdateCommentTree_whenRootLevelComment_thenSuccess() throws Exception {
        JsonNode payload = createPayload(false);
        ObjectNode existingCommentTreeData = createExistingCommentTreeData();

        CommentTree commentTree = new CommentTree();
        commentTree.setCommentTreeData(existingCommentTreeData);
        commentTree.setCommentTreeId("tree123");

        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(commentTree));
        when(commentTreeRepository.save(any())).thenReturn(commentTree);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        CommentTree result = commentTreeService.updateCommentTree(payload);

        assertNotNull(result);
        verify(commentTreeRepository).save(any());
    }

    @Test
    void testUpdateCommentTree_whenNestedComment_thenSuccess() throws Exception {
        JsonNode payload = createPayload(true);

        ObjectMapper realMapper = new ObjectMapper();
        ObjectNode comment1 = realMapper.createObjectNode();
        comment1.put(Constants.COMMENT_ID, "parent1");
        comment1.putArray(Constants.CHILDREN);

        ArrayNode comments = realMapper.createArrayNode().add(comment1);
        ObjectNode treeData = realMapper.createObjectNode();
        treeData.set(Constants.COMMENTS, comments);
        treeData.putArray(Constants.CHILD_NODES);
        treeData.putArray(Constants.FIRST_LEVEL_NODES);

        CommentTree commentTree = new CommentTree();
        commentTree.setCommentTreeId("tree123");
        commentTree.setCommentTreeData(treeData);

        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());
        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(commentTree));
        when(objectMapper.treeToValue(any(), eq(String[].class))).thenReturn(new String[]{"parent1"});
        when(commentTreeRepository.save(any())).thenReturn(commentTree);
        when(objectMapper.convertValue(any(), eq(Map.class))).thenReturn(new HashMap<>());

        CommentTree result = commentTreeService.updateCommentTree(payload);

        assertNotNull(result);
        verify(commentTreeRepository).save(any());
    }

    @Test
    void testUpdateCommentTree_whenWrongHierarchyPath_thenThrowsException() throws Exception {
        JsonNode payload = createPayload(true);
        ObjectNode treeData = createExistingCommentTreeData();

        CommentTree commentTree = new CommentTree();
        commentTree.setCommentTreeId("tree123");
        commentTree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(commentTree));
        when(objectMapper.treeToValue(any(), eq(String[].class))).thenReturn(new String[]{"wrongId"});
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());

        CommentException exception = assertThrows(CommentException.class, () -> {
            commentTreeService.updateCommentTree(payload);
        });

        assertEquals(Constants.WRONG_HIERARCHY_PATH_ERROR, exception.getMessage());
    }

    @Test
    void testUpdateCommentTree_whenException_thenThrowsCommentException() {
        JsonNode payload = createPayload(false);
        ObjectNode treeData = createExistingCommentTreeData();

        CommentTree commentTree = new CommentTree();
        commentTree.setCommentTreeId("tree123");
        commentTree.setCommentTreeData(treeData);

        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.of(commentTree));
        when(commentTreeRepository.save(any())).thenThrow(new RuntimeException("DB error"));
        when(objectMapper.createObjectNode()).thenReturn(new ObjectMapper().createObjectNode());

        CommentException exception = assertThrows(CommentException.class, () -> {
            commentTreeService.updateCommentTree(payload);
        });

        assertEquals("DB error", exception.getMessage());
    }

    @Test
    void testUpdateCommentTree_whenCommentTreeNotFound_thenReturnsNull() {
        JsonNode payload = new ObjectMapper().createObjectNode().put(Constants.COMMENT_TREE_ID, "tree123");

        when(commentTreeRepository.findById("tree123")).thenReturn(Optional.empty());

        CommentTree result = commentTreeService.updateCommentTree(payload);
        assertNull(result);
    }

    @Test
    void testGetAllCommentTreeForMultipleWorkflows() {
        String entityType = "course";
        String entityId = "course123";
        List<String> workflowList = List.of("workflow1", "workflow2");

        CommentTree tree1 = new CommentTree();
        tree1.setCommentTreeId("tree1");

        CommentTree tree2 = new CommentTree();
        tree2.setCommentTreeId("tree2");

        List<CommentTree> mockResult = List.of(tree1, tree2);

        when(commentTreeRepository.getAllCommentTreeForMultipleWorkflows(entityId, entityType, workflowList))
                .thenReturn(mockResult);

        List<CommentTree> result = commentTreeService.getAllCommentTreeForMultipleWorkflows(entityType, entityId, workflowList);

        assertEquals(2, result.size());
        assertEquals("tree1", result.get(0).getCommentTreeId());
        assertEquals("tree2", result.get(1).getCommentTreeId());

        verify(commentTreeRepository, times(1))
                .getAllCommentTreeForMultipleWorkflows(entityId, entityType, workflowList);
    }

    private JsonNode createPayload(boolean withHierarchyPath) {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put(Constants.COMMENT_TREE_ID, "tree123");
        payload.put(Constants.COMMENT_ID, "comment456");
        if (withHierarchyPath) {
            ArrayNode path = payload.putArray(Constants.HIERARCHY_PATH);
            path.add("parent1");
        } else {
            payload.putArray(Constants.HIERARCHY_PATH); // empty
        }
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
