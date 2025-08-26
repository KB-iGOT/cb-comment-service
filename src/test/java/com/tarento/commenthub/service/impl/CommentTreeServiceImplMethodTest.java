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
}

